package graphics.scenery.backends.vulkan

import graphics.scenery.*
import graphics.scenery.backends.*
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.vulkan.VulkanTexture.Companion.toVulkanFormat
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.lazyLogger
import org.lwjgl.system.jemalloc.JEmalloc
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkBufferCopy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Helper object for node texture loading and buffer creation.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
object VulkanNodeHelpers {
    val logger by lazyLogger()

    /**
     * Creates vertex buffers for a given [node] on [device].
     *
     * Will access the node's [state], allocate staging memory from [stagingPool], and GPU memory
     * from [geometryPool]. Command buffer allocation and submission is done via [commandPools] in
     * the given [queue]. Returns the modified [VulkanRendererMetadata].
     */
    fun createVertexBuffers(
        device: VulkanDevice,
        node: Node,
        state: VulkanRendererMetadata,
        stagingPool: VulkanBufferPool,
        geometryPool: VulkanBufferPool,
        commandPools: VulkanRenderer.CommandPools,
        queue: VulkanDevice.QueueWithMutex
    ): VulkanRendererMetadata {
        val geometry = node.geometryOrNull() ?: return state
        val vertices = geometry.vertices.duplicate()
        val normals = geometry.normals.duplicate()
        var texcoords = geometry.texcoords.duplicate()
        val indices = geometry.indices.duplicate()

        if(vertices.remaining() == 0) {
            return state
        }

        if (texcoords.remaining() == 0 && node is InstancedNode) {
            val buffer = JEmalloc.je_calloc(1, 4L * vertices.remaining() / geometry.vertexSize * geometry.texcoordSize)

            if(buffer == null) {
                logger.error("Could not allocate texcoords buffer with ${4L * vertices.remaining() / geometry.vertexSize * geometry.texcoordSize} bytes for ${node.name}")
                return state
            } else {
                geometry.texcoords = buffer.asFloatBuffer()
                texcoords = geometry.texcoords.asReadOnlyBuffer()
            }
        }

        val vertexAllocationBytes: Long = 4L * (vertices.remaining() + normals.remaining() + texcoords.remaining())
        val indexAllocationBytes: Long = 4L * indices.remaining()
        val fullAllocationBytes: Long = vertexAllocationBytes + indexAllocationBytes

        val stridedBuffer = JEmalloc.je_malloc(fullAllocationBytes)

        if(stridedBuffer == null) {
            logger.error("Allocation failed, skipping vertex buffer creation for ${node.name}.")
            return state
        }

        val fb = stridedBuffer.asFloatBuffer()
        val ib = stridedBuffer.asIntBuffer()

        state.vertexCount = vertices.remaining() / geometry.vertexSize
        logger.trace("${node.name} has ${vertices.remaining()} floats and ${texcoords.remaining() / geometry.texcoordSize} remaining")

        for (index in 0 until vertices.remaining() step 3) {
            fb.put(vertices.get())
            fb.put(vertices.get())
            fb.put(vertices.get())

            fb.put(normals.get())
            fb.put(normals.get())
            fb.put(normals.get())

            if (texcoords.remaining() > 0) {
                fb.put(texcoords.get())
                fb.put(texcoords.get())
            }
        }

        logger.trace("Adding {} bytes to strided buffer", indices.remaining() * 4)
        if (indices.remaining() > 0) {
            state.isIndexed = true
            ib.position(vertexAllocationBytes.toInt() / 4)

            for (index in 0 until indices.remaining()) {
                ib.put(indices.get())
            }
        }

        logger.trace("Strided buffer is now at {} bytes", stridedBuffer.remaining())

        val stagingBuffer = stagingPool.createBuffer(fullAllocationBytes.toInt())

        stagingBuffer.copyFrom(stridedBuffer)

        val vertexIndexBuffer = state.vertexBuffers["vertex+index"]
        val vertexBuffer = if(vertexIndexBuffer != null && vertexIndexBuffer.size >= fullAllocationBytes) {
            logger.debug("Reusing existing vertex+index buffer for {} update", node.name)
            vertexIndexBuffer
        } else {
            logger.debug("Creating new vertex+index buffer for {} with {} bytes", node.name, fullAllocationBytes)
            geometryPool.createBuffer(fullAllocationBytes.toInt())
        }

        logger.debug("Using VulkanBuffer {} for vertex+index storage, offset={}", vertexBuffer.vulkanBuffer.toHexString(), vertexBuffer.bufferOffset)

        logger.debug("Initiating copy with 0->${vertexBuffer.bufferOffset}, size=$fullAllocationBytes")
        val copyRegion = VkBufferCopy.calloc(1)
            .srcOffset(0)
            .dstOffset(vertexBuffer.bufferOffset)
            .size(fullAllocationBytes * 1L)

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            VK10.vkCmdCopyBuffer(this,
                stagingBuffer.vulkanBuffer,
                vertexBuffer.vulkanBuffer,
                copyRegion)
            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        copyRegion.free()

        state.vertexBuffers.put("vertex+index", vertexBuffer)?.run {
            // check if vertex buffer has been replaced, if yes, close the old one
            if(this != vertexBuffer) { close() }
        }
        state.indexOffset = vertexBuffer.bufferOffset + vertexAllocationBytes
        state.indexCount = geometry.indices.remaining()

        JEmalloc.je_free(stridedBuffer)
        stagingBuffer.close()

        return state
    }

    /**
     * Updates instance buffers for a given [node] on [device]. Modifies the [node]'s [state]
     * and allocates necessary command buffers from [commandPools] and submits to [queue]. Returns the [node]'s modified [VulkanRendererMetadata].
     */
    fun updateInstanceBuffer(device: VulkanDevice, node: InstancedNode, state: VulkanRendererMetadata, commandPools: VulkanRenderer.CommandPools, queue: VulkanDevice.QueueWithMutex): VulkanRendererMetadata {
        logger.trace("Updating instance buffer for {}", node.name)

        // parentNode.instances is a CopyOnWrite array list, and here we keep a reference to the original.
        // If it changes in the meantime, no problemo.
        val instances = node.instances

        if (instances.isEmpty()) {
            logger.debug("$node has no child instances attached, returning.")
            return state
        }

        // TODO make maxInstanceUpdateCount property of InstancedNode
        val maxUpdates = node.metadata["MaxInstanceUpdateCount"] as? AtomicInteger
        if((maxUpdates?.get() ?: 1) < 1) {
            logger.debug("Instances updates blocked for ${node.name}, returning")
            return state
        }

        // first we create a fake UBO to gauge the size of the needed properties
        val ubo = VulkanUBO(device)
        ubo.fromInstance(instances.first())

        val instanceBufferSize = ubo.getSize() * instances.size

        val instanceStagingBuffer = state.vertexBuffers["instanceStaging"]
        val stagingBuffer = if(instanceStagingBuffer != null && instanceStagingBuffer.size >= instanceBufferSize) {
            instanceStagingBuffer
        } else {
            logger.debug("Creating new staging buffer")
            val buffer = VulkanBuffer(device,
                (1.2 * instanceBufferSize).toLong(),
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                wantAligned = true)

            state.vertexBuffers["instanceStaging"] = buffer
            buffer
        }

        ubo.updateBackingBuffer(stagingBuffer)
        ubo.createUniformBuffer()

        val index = AtomicInteger(0)
        instances.parallelStream().forEach { instancedNode ->
            if(instancedNode.visible) {
                instancedNode.spatialOrNull()?.updateWorld(true, false)

                stagingBuffer.stagingBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).run {
                    ubo.populateParallel(this, offset = index.getAndIncrement() * ubo.getSize() * 1L, elements = instancedNode.instancedProperties)
                }
            }
        }

        stagingBuffer.stagingBuffer.position(stagingBuffer.stagingBuffer.limit())
        stagingBuffer.copyFromStagingBuffer()

        val existingInstanceBuffer = state.vertexBuffers["instance"]
        val instanceBuffer = if (existingInstanceBuffer != null
            && existingInstanceBuffer.size >= instanceBufferSize
            && existingInstanceBuffer.size < 1.5*instanceBufferSize) {
            existingInstanceBuffer
        } else {
            logger.debug("Instance buffer for ${node.name} needs to be reallocated due to insufficient size ($instanceBufferSize vs ${state.vertexBuffers["instance"]?.size ?: "<not allocated yet>"})")
            state.vertexBuffers["instance"]?.close()

            val buffer = VulkanBuffer(device,
                instanceBufferSize * 1L,
                VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                wantAligned = true)

            state.vertexBuffers["instance"] = buffer
            buffer
        }

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            val copyRegion = VkBufferCopy.calloc(1)
                .size(instanceBufferSize * 1L)

            VK10.vkCmdCopyBuffer(this,
                stagingBuffer.vulkanBuffer,
                instanceBuffer.vulkanBuffer,
                copyRegion)

            copyRegion.free()
            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }

        state.instanceCount = index.get()//instances.size

        maxUpdates?.decrementAndGet()
        return state
    }

    /**
     * Loads or reloads the textures for [node], updating it's internal renderer state stored in [s].
     *
     * [defaultTextures] for fallback need to be given, as well as the [VulkanRenderer]'s [textureCache].
     * Necessary command buffers will be allocated from [commandPools] and submitted to [queue].
     *
     * Returns a [Pair] of [Boolean], indicating whether contents or descriptor set have changed.
     */
    fun loadTexturesForNode(device: VulkanDevice, node: Node, s: VulkanRendererMetadata, defaultTextures: Map<String, VulkanTexture>, textureCache: MutableMap<Texture, VulkanTexture>, commandPools: VulkanRenderer.CommandPools, queue: VulkanDevice.QueueWithMutex, transferQueue: VulkanDevice.QueueWithMutex = queue): Pair<Boolean, Boolean> {
        val material = node.materialOrNull() ?: return Pair(false, false)
        val defaultTexture = defaultTextures["DefaultTexture"] ?: throw IllegalStateException("Default fallback texture does not exist.")
        // if a node is not yet initialized, we'll definitely require a new DS
        var descriptorUpdated = !node.initialized
        var contentUpdated = false

        val last = s.texturesLastSeen
        val now = System.nanoTime()
        material.textures.forEachChanged(last) { (type, texture) ->
            contentUpdated = true
            val slot = VulkanRendererMetadata.textureTypeToSlot(type)
            val generateMipmaps = Texture.mipmappedObjectTextures.contains(type)

            logger.debug("${node.name} will have $type texture from $texture in slot $slot")

            val existing = textureCache[texture]
            if (existing == null) {
                try {
                    logger.debug("Loading texture {} for {}", texture, node.name)

                    val miplevels = if (generateMipmaps && texture.mipmap) {
                        floor(ln(min(texture.dimensions.x() * 1.0, texture.dimensions.y() * 1.0)) / ln(2.0)).toInt()
                    } else {
                        1
                    }

                    val existingTexture = s.textures[type]
                    // We take care here that the potentially to-be-reused texture is not
                    // scenery's default textures, and otherwise compatible with the new one.
                    val t: VulkanTexture = if (existingTexture != null
                        && existingTexture.canBeReused(texture, miplevels, device)
                        && existingTexture != defaultTexture) {
                        existingTexture.texture = texture
                        existingTexture
                    } else {
                        descriptorUpdated = true
                        VulkanTexture(device, commandPools, queue, transferQueue, texture, miplevels)
                    }

                    texture.contents?.let { contents ->
                        logger.debug("Copying contents of size ${contents.remaining()/1024/1024}M")
                        t.copyFrom(contents.duplicate())
                    }

                    if (texture is UpdatableTexture && texture.hasConsumableUpdates()) {
                        t.copyFrom(ByteBuffer.allocate(0))
                    }

                    if(descriptorUpdated) {
                        t.createSampler(texture)
                    }

                    // add new texture to texture list and cache, and close old texture
                    s.textures[type] = t

                    if(texture !is UpdatableTexture) {
                        textureCache[texture] = t
                    }
                } catch (e: Exception) {
                    logger.warn("Could not load texture for ${node.name}: $e")
                    e.printStackTrace()
                }
            } else {
                if(s.textures[type] != existing) {
                    descriptorUpdated = true
                }
                s.textures[type] = existing
            }
        }

        if(material.textures.isEmpty()) {
            s.textures.clear()
        }

        s.texturesLastSeen = now

        val isCompute = material is ShaderMaterial && ((material as? ShaderMaterial)?.isCompute() ?: false)
        if(!isCompute) {
            Texture.objectTextures.forEach {
                s.defaultTexturesFor.clear()
                if (!s.textures.containsKey(it)) {
                    s.textures.putIfAbsent(it, defaultTexture)
                    s.defaultTexturesFor.add(it)
                }
            }
        }

        return contentUpdated to descriptorUpdated
    }

    /**
     * Initialises custom shaders for a given [node] on [device]. Adds optional initialisers e.g. for
     * resizing, if [addInitializer] is set to true. Such initialiser is added to [lateResizeInitializers].
     * [buffers] for UBO access need to be given.
     *
     * Returns true if the node has been given a custom shader, and false if not.
     */
    fun initializeCustomShadersForNode(device: VulkanDevice, node: Node, addInitializer: Boolean = true, renderpasses: Map<String, VulkanRenderpass>, lateResizeInitializers: MutableMap<Renderable, () -> Any>, buffers: VulkanRenderer.DefaultBuffers): Boolean {

        val renderable = node.renderableOrNull() ?: return false
        val material = node.materialOrNull() ?: return false
        if(!(material.blending.transparent || material is ShaderMaterial || material.cullingMode != Material.CullingMode.Back || material.wireframe)) {
            logger.debug("Using default renderpass material for ${node.name}")
            renderpasses
                .filter { it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights }
                .forEach {
                    it.value.removePipeline(renderable)
                }

            lateResizeInitializers.remove(renderable)
            return false
        }

        if(addInitializer) {
            lateResizeInitializers.remove(renderable)
        }

        renderable.rendererMetadata()?.let { s ->
            renderpasses.filter { it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights }
                .map { (passName, pass) ->
                    val shaders = when (material) {
                        is ShaderMaterial -> {
                            logger.debug("Initializing preferred pipeline for ${node.name} in pass $passName from ShaderMaterial")
                            material.shaders
                        }
                        else -> {
                            logger.debug("Initializing pass-default shader preferred pipeline for ${node.name} in pass $passName")
                            Shaders.ShadersFromFiles(pass.passConfig.shaders.map { "shaders/$it" }.toTypedArray())
                        }
                    }

                    logger.debug("Shaders are: $shaders")

                    val shaderModules = ShaderType.values().mapNotNull { type ->
                        try {
                            VulkanShaderModule.getFromCacheOrCreate(device, "main", shaders.get(Shaders.ShaderTarget.Vulkan, type))
                        } catch (e: ShaderNotFoundException) {
                            null
                        } catch (e: ShaderConsistencyException) {
                            logger.warn("${e.message} - Falling back to default shader.")
                            if(logger.isDebugEnabled) {
                                e.printStackTrace()
                            }
                            return false
                        }
                    }

                    val pipeline = pass.initializePipeline(shaderModules,
                        material.cullingMode,
                        material.depthTest,
                        material.depthWrite,
                        material.depthOp,
                        material.blending,
                        material.wireframe,
                        material.wireframeWidth,
                        s.vertexDescription
                    )
                    pass.registerPipelineForNode(pipeline, renderable)
                }


            if (renderable.needsShaderPropertyUBO()) {
                renderpasses.filter {
                    (it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights) &&
                        it.value.passConfig.renderTransparent == material.blending.transparent
                }.forEach { pass ->
                    val dsl = pass.value.initializeShaderPropertyDescriptorSetLayout()

                    logger.debug("Initializing shader properties for ${node.name} in pass ${pass.key}")
                    val order = pass.value.getShaderPropertyOrder(renderable)

                    val shaderPropertyUbo = VulkanUBO(device, backingBuffer = buffers.ShaderProperties)
                    with(shaderPropertyUbo) {
                        name = "ShaderProperties"

                        order.forEach { (name, offset) ->
                            // TODO: See whether returning 0 on non-found shader property has ill side effects
                            add(name, { renderable.parent.getShaderProperty(name) ?: 0 }, offset)
                        }

                        val result = this.createUniformBuffer()

                        val ds = device.createDescriptorSetDynamic(
                                dsl,
                                1,
                                buffers.ShaderProperties,
                                size = maxOf(result.range, 2048)
                            )
                        s.requiredDescriptorSets["ShaderProperties"] = ds
                        s.UBOs["${pass.key}-ShaderProperties"] = ds to this
                    }
                }

            }

            if(addInitializer) {
                lateResizeInitializers[renderable] = {
                    val reloaded = initializeCustomShadersForNode(device, node, addInitializer = false, renderpasses, lateResizeInitializers, buffers)

                    if(reloaded) {
                        renderable.rendererMetadata()?.texturesToDescriptorSets(device,
                            renderpasses.filter { pass -> pass.value.passConfig.type != RenderConfigReader.RenderpassType.quad },
                            renderable)
                    }
                }
            }

//             TODO: Figure out if this can be avoided for the BDV integration
            s.clearTextureDescriptorSets()

            return true
        }

        return false
    }

    private fun Renderable.needsShaderPropertyUBO(): Boolean = this
        .parent
        .javaClass
        .kotlin
        .memberProperties
        .filter { it.findAnnotation<ShaderProperty>() != null }
        .count() > 0

    /**
     * Returns true if the current VulkanTexture can be reused to store the information in the [Texture]
     * [other]. Returns false otherwise.
     */
    fun VulkanTexture.canBeReused(other: Texture, miplevels: Int, device: VulkanDevice): Boolean {
        return this.device == device &&
                this.width == other.dimensions.x() &&
                this.height == other.dimensions.y() &&
                this.depth == other.dimensions.z() &&
                this.format == other.toVulkanFormat() &&
                this.mipLevels == miplevels
    }

    /**
     * Returns a node's [VulkanRenderer] metadata, [VulkanRendererMetadata], if available.
     */
    fun Renderable.rendererMetadata(): VulkanRendererMetadata? {
        return this.metadata["VulkanRenderer"] as? VulkanRendererMetadata
    }
}
