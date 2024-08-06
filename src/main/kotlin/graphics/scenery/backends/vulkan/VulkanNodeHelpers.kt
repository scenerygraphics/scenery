package graphics.scenery.backends.vulkan

import graphics.scenery.*
import graphics.scenery.attribute.buffers.BufferType
import graphics.scenery.attribute.buffers.Buffers
import graphics.scenery.backends.*
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.vulkan.VulkanTexture.Companion.toVulkanFormat
import graphics.scenery.textures.Texture
import graphics.scenery.textures.UpdatableTexture
import graphics.scenery.utils.lazyLogger
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.jemalloc.JEmalloc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
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
        val stridedVertexBuffer = JEmalloc.je_malloc(vertexAllocationBytes)

        if(stridedVertexBuffer == null) {
            logger.error("Allocation failed, skipping vertex buffer creation for ${node.name}.")
            return state
        }

        val fb = stridedVertexBuffer.asFloatBuffer()

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

        val stagingVertexBuffer = stagingPool.createBuffer(vertexAllocationBytes.toInt(), "StagingVertexBuffer")
        stagingVertexBuffer.copyFrom(stridedVertexBuffer)

        val vertexBuffer = state.geometryBuffers["vertex"]
        val vBuffer = if(vertexBuffer != null && vertexBuffer.size >= vertexAllocationBytes) {
            logger.debug("Reusing existing vertex buffer for {} update", node.name)
            vertexBuffer
        } else {
            logger.debug("Creating new vertex buffer for {} with {} bytes", node.name, vertexAllocationBytes)
            geometryPool.createBuffer(vertexAllocationBytes.toInt(), "VertexBuffer")
        }

        logger.debug("Using VulkanBuffer {} for vertex storage, offset={}", vBuffer.vulkanBuffer.toHexString(), vBuffer.bufferOffset)
        logger.debug("Initiating copy with 0->${vBuffer.bufferOffset}, size=$vertexAllocationBytes")
        val copyRegion = VkBufferCopy.calloc(1)
            .srcOffset(0)
            .dstOffset(vBuffer.bufferOffset)
            .size(vertexAllocationBytes * 1L)

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            VK10.vkCmdCopyBuffer(this,
                stagingVertexBuffer.vulkanBuffer,
                vBuffer.vulkanBuffer,
                copyRegion)
            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }
        copyRegion.free()

        state.geometryBuffers.put("vertex", vBuffer)?.run {
            // check if vertex buffer has been replaced, if yes, close the old one
            if(this != vBuffer) { close() }
        }

        JEmalloc.je_free(stridedVertexBuffer)
        stagingVertexBuffer.close()



        if (indices.remaining() <= 0) {
            return state
        }
        state.isIndexed = true
        state.indexCount = geometry.indices.remaining()

        val indexAllocationBytes: Long = 4L * indices.remaining()
        val stridedIndexBuffer = JEmalloc.je_malloc(indexAllocationBytes)
        if(stridedIndexBuffer == null) {
            logger.error("Allocation failed, skipping vertex buffer creation for ${node.name}.")
            return state
        }

        val ib = stridedIndexBuffer.asIntBuffer()
        for (index in 0 until indices.remaining()) {
            ib.put(indices.get())
        }

        val stagingIndexBuffer = stagingPool.createBuffer(indexAllocationBytes.toInt(), "StagingIndexBuffer")
        stagingIndexBuffer.copyFrom(stridedIndexBuffer)

        val indexBuffer = state.geometryBuffers["index"]
        val iBuffer = if(indexBuffer != null && indexBuffer.size >= indexAllocationBytes) {
            logger.debug("Reusing existing index buffer for {} update", node.name)
            indexBuffer
        } else {
            logger.debug("Creating new index buffer for {} with {} bytes", node.name, indexAllocationBytes)
            geometryPool.createBuffer(indexAllocationBytes.toInt(), "IndexBuffer")
        }

        logger.debug("Using VulkanBuffer {} for index storage, offset={}", iBuffer.vulkanBuffer.toHexString(), iBuffer.bufferOffset)
        logger.debug("Initiating copy with 0->${iBuffer.bufferOffset}, size=$indexAllocationBytes")
        val copyIndexRegion = VkBufferCopy.calloc(1)
            .srcOffset(0)
            .dstOffset(iBuffer.bufferOffset)
            .size(indexAllocationBytes * 1L)

        with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
            VK10.vkCmdCopyBuffer(this,
                stagingIndexBuffer.vulkanBuffer,
                iBuffer.vulkanBuffer,
                copyIndexRegion)
            this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true)
        }
        copyIndexRegion.free()

        state.geometryBuffers.put("index", iBuffer)?.run {
            // check if vertex buffer has been replaced, if yes, close the old one
            if(this != iBuffer) { close() }
        }
        JEmalloc.je_free(stridedIndexBuffer)
        stagingIndexBuffer.close()


        return state
    }

    fun updateShaderStorageBuffers(
    device: VulkanDevice,
    node: Node,
    state: VulkanRendererMetadata,
    stagingPool: VulkanBufferPool,
    ssboUploadPool: VulkanBufferPool,
    ssboDownloadPool: VulkanBufferPool,
    commandPools: VulkanRenderer.CommandPools,
    queue: VulkanDevice.QueueWithMutex
    ): VulkanRendererMetadata {

        node.ifBuffers {
            val descriptors = (0 until buffers.size).map { Pair(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1) }
            val dsl = device.createDescriptorSetLayout(descriptors, 0, VK10.VK_SHADER_STAGE_ALL)

            var stagingUpdated = false
            val ssboUbos = mutableListOf<Pair<Int, VulkanUBO.UBODescriptor>>()
            buffers.forEach { (name, description) ->
                val type = description.type
                val usage = description.usage
                val layout = if(type is BufferType.Primitive<*>) {
                    return@forEach
                } else {
                    type as BufferType.Custom
                    type.layout
                }

                //This will only work if the parent node is processes before the child node.
                // If the parent depends on the children, a different approach must be taken (maybe skip the parent the first time and initialize it in the next frame)
                if(description.inheritance) {
                    //For now, assume that the parent creates and fills the needed buffer, therefore check that the parent exists
                    node.parent?.let { parent ->
                        //if so, retrieve the buffer with the same name (could later be switched with an ID and
                        //individual names that more describe their usage in the shaders) and set this buffer to the parents buffer. TODO: check if it should be a duplicate, also might not be prone to sync errors
                        parent.ifBuffers {
                            this.buffers.get(name)?.let {
                                description.buffer = it.buffer
                            }
                        }
                        // also add the SSBO to this node
                        parent.ifRenderable {
                            rendererMetadata()?.SSBOBuffers?.get(name)?.let { vulkanBuffer ->
                                state.SSBOBuffers[name] = vulkanBuffer
                                val ssboUbo = VulkanUBO(device, ubo = layout)
                                ssboUbo.updateBackingBuffer(vulkanBuffer)
                                ssboUbo.createUniformBuffer(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                                ssboUbos.add(1 to ssboUbo.descriptor)
                            }
                            if(!state.SSBOBuffers.containsKey(name))
                            {
                                logger.error("SSBO with name $name couldn't be found in parent!")
                            }
                        }
                    }

                    return@forEach
                }

                description.buffer?.let { buffer ->
                    val backingBuffer = buffer.duplicate()
                    backingBuffer as ByteBuffer

                    val ssboSize = backingBuffer.remaining()
                    if (ssboSize <= 0)
                        return@forEach


                    var download = false
                    val ssboBufferCurrent = state.SSBOBuffers[name]
                    val ssboBuffer = if (ssboBufferCurrent != null && ssboBufferCurrent.size >= ssboSize.toLong()) {
                        ssboBufferCurrent
                    } else {
                        logger.debug("Creating new SSBO Buffer")
                        state.SSBOBuffers[name]?.close()

                        val buffer = when (usage) {
                            Buffers.BufferUsage.Upload -> ssboUploadPool.createBuffer(ssboSize, name = "UploadBuffer_$name")
                            Buffers.BufferUsage.Download -> {
                                download = true
                                ssboDownloadPool.createBuffer(ssboSize, name = "DownloadBuffer_$name")
                            }
                        }
                        state.SSBOBuffers[name] = buffer
                        buffer
                    }

                    if(!download) {
                        // TODO: staging might not be worth caching, especially because it seems that creating a VulkanBuffer creates a staging instance internally
                        val ssboStagingBuffer = state.SSBOBuffers[name + "Staging"]
                        val stagingBuffer = if (ssboStagingBuffer != null && ssboStagingBuffer.size >= ssboSize) {
                            ssboStagingBuffer
                        } else {
                            logger.debug("Creating new SSBO Staging Buffer")
                            state.SSBOBuffers[name + "Staging"]?.close()

                            val buffer = stagingPool.createBuffer(ssboSize)
                            state.SSBOBuffers[name + "Staging"] = buffer
                            stagingUpdated = true
                            buffer
                        }
                        stagingBuffer.copyFrom(backingBuffer)

                        stackPush().use { stack ->
                            with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                                val copyRegion = VkBufferCopy.calloc(1, stack)
                                    .size(ssboSize * 1L)
                                    .srcOffset(stagingBuffer.suballocation?.offset?.toLong() ?: 0L)
                                    .dstOffset(ssboBuffer.suballocation?.offset?.toLong() ?: 0L)

                                VK10.vkCmdCopyBuffer(
                                    this,
                                    stagingBuffer.vulkanBuffer,
                                    ssboBuffer.vulkanBuffer,
                                    copyRegion
                                )

                                this.endCommandBuffer(
                                    device,
                                    commandPools.Standard,
                                    queue,
                                    flush = true,
                                    dealloc = true
                                )
                            }
                        }
                    }
                    val ssboUbo = VulkanUBO(device, ubo = layout)
                    ssboUbo.updateBackingBuffer(ssboBuffer)
                    ssboUbo.createUniformBuffer(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    ssboUbos.add(1 to ssboUbo.descriptor)

                    return@forEach
                }
            }

            var ds = state.requiredDescriptorSets["ssbos"]
            if(stagingUpdated || ds == null)
            {
                ds = device.createDescriptorSet(
                    dsl, ssboUbos.toList(), listOf(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER))
                state.requiredDescriptorSets["ssbos"] = ds
            }
            state.SSBOs["ssbos"] = ds
        }
        return state
    }

    fun downloadShaderStorageBuffers(
        device: VulkanDevice,
        node: Node,
        state: VulkanRendererMetadata,
        commandPools: VulkanRenderer.CommandPools,
        queue: VulkanDevice.QueueWithMutex
    ): VulkanRendererMetadata {

        node.ifBuffers {
            downloadRequests.forEach { name ->
                logger.info("Requesting download for {}", name)

                var size = 0
                val bufferDownloadDest = if(buffers.containsKey(name)) {
                    buffers[name]?.let {
                        size = it.size
                        it.buffer?.let {
                            it.duplicate() as ByteBuffer
                        }
                    }
                } else {
                    logger.error("Buffer {} not present in Buffers property!", name)
                    return@forEach
                }

                val stagingBuffer = VulkanBuffer(device, size * 1L, VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, true, name = "SSBO_StagingBuffer")


                val ssboBuffer = state.SSBOBuffers[name]
                val downloadBuffer = if(ssboBuffer != null) {
                    ssboBuffer
                } else {
                    logger.error("SSBOBuffer {} not defined in node state!", name)
                    return@forEach
                }

                val copyFence = stackPush().use { stack ->
                    val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                        .pNext(MemoryUtil.NULL)
                        .flags(0)

                    val fence = VU.getLong("vkCreateFence",
                        { vkCreateFence(device.vulkanDevice, fenceCreateInfo, null, this) }, {})
                    logger.debug("Created semaphore {}", fence.toHexString().lowercase())
                    fence
                }

                stackPush().use { stack ->
                    with(VU.newCommandBuffer(device, commandPools.Standard, autostart = true)) {
                        val writeBarrier = VkMemoryBarrier.calloc(1, stack)
                            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                            .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)

                        VK10.vkCmdPipelineBarrier(
                            this,
                            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT,
                            0,
                            writeBarrier,
                            null,
                            null
                            )

                        val copyRegion = VkBufferCopy.calloc(1, stack)
                            .size(size * 1L)
                            .srcOffset(downloadBuffer.suballocation?.offset?.toLong() ?: 0L)
                            .dstOffset(stagingBuffer.suballocation?.offset?.toLong() ?: 0L)

                        VK10.vkCmdCopyBuffer(
                            this,
                            downloadBuffer.vulkanBuffer,
                            stagingBuffer.vulkanBuffer,
                            copyRegion
                        )

                        val copyBarrier = VkMemoryBarrier.calloc(1, stack)
                            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_HOST_READ_BIT)

                        VK10.vkCmdPipelineBarrier(
                            this,
                            VK_PIPELINE_STAGE_TRANSFER_BIT,
                            VK_PIPELINE_STAGE_HOST_BIT,
                            0,
                            copyBarrier,
                            null,
                            null
                        )

                        this.endCommandBuffer(device, commandPools.Standard, queue, flush = true, dealloc = true, fence = copyFence)
                    }
                }

                if(bufferDownloadDest != null) {
                    VK10.vkWaitForFences(device.vulkanDevice, copyFence, true, Int.MAX_VALUE.toLong())
                    stagingBuffer.copyTo(bufferDownloadDest)
                } else {
                    logger.error("Try to copy to undefined buffer {}", name)
                }

                //stagingBuffer.close()
            }
        }

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

        val instanceStagingBuffer = state.geometryBuffers["instanceStaging"]
        val stagingBuffer = if(instanceStagingBuffer != null && instanceStagingBuffer.size >= instanceBufferSize) {
            instanceStagingBuffer
        } else {
            logger.debug("Creating new staging buffer")
            val buffer = VulkanBuffer(device,
                (1.2 * instanceBufferSize).toLong(),
                VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                wantAligned = true,
                name = "InstanceStagingBuffer")

            state.geometryBuffers["instanceStaging"] = buffer
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

        val existingInstanceBuffer = state.geometryBuffers["instance"]
        val instanceBuffer = if (existingInstanceBuffer != null
            && existingInstanceBuffer.size >= instanceBufferSize
            && existingInstanceBuffer.size < 1.5*instanceBufferSize) {
            existingInstanceBuffer
        } else {
            logger.debug("Instance buffer for ${node.name} needs to be reallocated due to insufficient size ($instanceBufferSize vs ${state.geometryBuffers["instance"]?.size ?: "<not allocated yet>"})")
            state.geometryBuffers["instance"]?.close()

            val buffer = VulkanBuffer(device,
                instanceBufferSize * 1L,
                VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                wantAligned = true,
                name = "InstanceBuffer")

            state.geometryBuffers["instance"] = buffer
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
    fun initializeCustomShadersForNode(
        device: VulkanDevice,
        node: Node,
        addInitializer: Boolean = true,
        renderpasses: Map<String, VulkanRenderpass>,
        lateResizeInitializers: MutableMap<Renderable, () -> Any>,
        buffers: VulkanRenderer.DefaultBuffers)
    : Boolean {

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
            renderpasses.filter { it.value.passConfig.type == RenderConfigReader.RenderpassType.geometry
                || it.value.passConfig.type == RenderConfigReader.RenderpassType.lights }
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
