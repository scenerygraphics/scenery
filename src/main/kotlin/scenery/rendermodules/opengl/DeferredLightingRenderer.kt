package scenery.rendermodules.opengl

import cleargl.GLFramebuffer
import cleargl.GLMatrix
import cleargl.GLProgram
import cleargl.GLTexture
import com.jogamp.common.nio.Buffers
import com.jogamp.opengl.GL
import com.jogamp.opengl.GL4
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scenery.*
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */

class DeferredLightingRenderer {
    protected var logger: Logger = LoggerFactory.getLogger("DeferredLightingRenderer")
    protected var gl: GL4
    protected var width: Int
    protected var height: Int
    protected var geometryBuffer: GLFramebuffer
    protected var hdrBuffer: GLFramebuffer
    protected var lightingPassProgram: GLProgram
    protected var hdrPassProgram: GLProgram

    protected var textures = HashMap<String, GLTexture>()

    protected var debugBuffers = 0;
    protected var doSSAO = 1;
    protected var doHDR = 1;
    protected var exposure = 0.02f;
    protected var gamma = 2.2f;

    constructor(gl: GL4, width: Int, height: Int) {
        this.gl = gl
        this.width = width
        this.height = height

        // create 32bit position buffer, 16bit normal buffer, 8bit diffuse buffer and 24bit depth buffer
        geometryBuffer = GLFramebuffer(this.gl, width, height)
        geometryBuffer.addFloatRGBBuffer(this.gl, 32)
        geometryBuffer.addFloatRGBBuffer(this.gl, 16)
        geometryBuffer.addUnsignedByteRGBABuffer(this.gl, 8)
        geometryBuffer.addDepthBuffer(this.gl, 24, 1)

        geometryBuffer.checkAndSetDrawBuffers(this.gl)
        logger.info(geometryBuffer.toString())

        // create HDR buffer
        hdrBuffer = GLFramebuffer(this.gl, width, height)
        hdrBuffer.addFloatRGBBuffer(this.gl, 32)

        lightingPassProgram = GLProgram.buildProgram(gl, DeferredLightingRenderer::class.java,
                arrayOf("shaders/Dummy.vert", "shaders/FullscreenQuadGenerator.geom", "shaders/DeferredLighting.frag"))

        hdrPassProgram = GLProgram.buildProgram(gl, DeferredLightingRenderer::class.java,
                arrayOf("shaders/Dummy.vert", "shaders/FullscreenQuadGenerator.geom", "shaders/HDR.frag"))
    }

    protected fun GeometryType.toOpenGLType(): Int {
        return when(this) {
            GeometryType.TRIANGLE_STRIP -> GL.GL_TRIANGLE_STRIP
            GeometryType.POLYGON -> GL.GL_TRIANGLES
            GeometryType.TRIANGLES -> GL.GL_TRIANGLES
            GeometryType.TRIANGLE_FAN -> GL.GL_TRIANGLE_FAN
            GeometryType.POINTS -> GL.GL_POINTS
            GeometryType.LINE -> GL.GL_LINE_STRIP
        }
    }


    fun toggleDebug() {
        if(debugBuffers == 0) {
            debugBuffers = 1;
        } else {
            debugBuffers = 0;
        }
    }

    fun toggleSSAO() {
        if(doSSAO == 0) {
            logger.info("SSAO is now on")
            doSSAO = 1;
        } else {
            logger.info("SSAO is now off")
            doSSAO = 0;
        }
    }

    fun toggleHDR() {
        if(doHDR == 0) {
            logger.info("HDR is now on")
            doHDR = 1;
        } else {
            logger.info("HDR is now on")
            doHDR = 0
        }
    }

    fun increaseExposure() {
        exposure += 0.05f
    }

    fun decreaseExposure() {
        if(exposure - 0.05f > 0.0f) {
            exposure -= 0.05f
        }
    }

    fun increaseGamma() {
        gamma += 0.05f
    }

    fun decreaseGamma() {
        if(gamma - 0.05f > 0.0f) {
            gamma -= 0.05f
        }
    }

    fun getOpenGLObjectStateFromNode(node: Node): OpenGLObjectState {
        return node.metadata.get("DeferredLightingRenderer") as OpenGLObjectState
    }

    fun initializeScene(scene: Scene) {
        scene.discover(scene, { it is HasGeometry })
                .forEach { it ->
            it.metadata.put("DeferredLightingRenderer", OpenGLObjectState())
            initializeNode(it)
        }

        logger.info("Initialized ${textures.size} textures")
    }

    /*
        the first texture units are reserved for the geometry buffer
     */
    protected fun textureTypeToUnit(type: String): Int {
        return geometryBuffer.boundBufferNum + when(type) {
            "ambient"       -> 0
            "diffuse"       -> 1
            "specular"      -> 2
            "normal"        -> 3
            "displacement"  -> 4
            else -> { logger.warn("Unknown texture type $type"); 10 }
        }
    }

    fun render(scene: Scene) {
        geometryBuffer.checkAndSetDrawBuffers(gl)

        val renderOrderList = ArrayList<Node>()
        val cam: Camera = scene.findObserver()
        var view: GLMatrix
        var mv: GLMatrix
        var mvp: GLMatrix
        var proj: GLMatrix

        gl.glEnable(GL.GL_DEPTH_TEST)
        gl.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)

        gl.glEnable(GL.GL_CULL_FACE)
        gl.glFrontFace(GL.GL_CCW)
        gl.glCullFace(GL.GL_BACK)

        gl.glDepthFunc(GL.GL_LEQUAL)

        scene.discover(scene, { n -> n is Renderable && n is HasGeometry && n.visible }).forEach {
            renderOrderList.add(it)
        }

        // depth-sorting based on camera position
//        renderOrderList.sort {
//            a, b ->
//            (a.position.z() - b.position.z()).toInt()
//        }

        cam.view?.setCamera(cam.position, cam.position + cam.forward, cam.up)

        val instanceGroups = renderOrderList.groupBy { it.instanceOf }

//        System.err.println("Instance groups:\n${instanceGroups.keys.map {
//            key ->
//            "  <${key?.name}> " + instanceGroups.get(key)!!.map { it.name }.joinToString(", ") + "\n"
//        }.joinToString("")}")

        instanceGroups.get(null)?.forEach nonInstancedDrawing@ { n ->
            if(n in instanceGroups.keys) {
                return@nonInstancedDrawing
            }

            val s = getOpenGLObjectStateFromNode(n)
            n.updateWorld(true, false)

            mv = cam.view!!.clone().mult(cam.rotation)
            mv.mult(n.world)

            proj = cam.projection!!.clone()
            mvp = proj.clone()
            mvp.mult(mv)

            val program: GLProgram? = s.program

            program?.let {
                program.use(gl)
                program.getUniform("ModelMatrix")!!.setFloatMatrix(n.model, false);
                program.getUniform("ModelViewMatrix")!!.setFloatMatrix(mv, false)
                program.getUniform("ProjectionMatrix")!!.setFloatMatrix(cam.projection, false)
                program.getUniform("MVP")!!.setFloatMatrix(mvp, false)

                program.getUniform("Material.Shinyness").setFloat(0.001f);

                if (n.material != null) {
                    program.getUniform("Material.Ka").setFloatVector(n.material!!.ambient);
                    program.getUniform("Material.Kd").setFloatVector(n.material!!.diffuse);
                    program.getUniform("Material.Ks").setFloatVector(n.material!!.specular);

                    if(n.material!!.doubleSided) {
                        gl.glDisable(GL.GL_CULL_FACE)
                    }
                } else {
                    program.getUniform("Material.Ka").setFloatVector3(n.position.toFloatBuffer());
                    program.getUniform("Material.Kd").setFloatVector3(n.position.toFloatBuffer());
                    program.getUniform("Material.Ks").setFloatVector3(n.position.toFloatBuffer());
                }

                var samplerIndex = 5;
                s.textures.forEach { type, glTexture ->
                    samplerIndex = textureTypeToUnit(type)
                    if(glTexture != null) {
                        gl.glActiveTexture(GL.GL_TEXTURE0 + samplerIndex)
                        gl.glBindTexture(GL.GL_TEXTURE_2D, glTexture.id)
                        program.getUniform("ObjectTextures[" + (samplerIndex-geometryBuffer.boundBufferNum) + "]").setInt(samplerIndex)
                    }
                    samplerIndex++
                }

                if(s.textures.size > 0){
                    program.getUniform("materialType").setInt(1)
                }

                if(s.textures.containsKey("normal")) {
                    program.getUniform("materialType").setInt(3)
                }
            }

            if(n is HasGeometry) {
                n.preDraw()
            }

            drawNode(n)
        }

        instanceGroups.keys.filterNotNull().forEach instancedDrawing@ { n ->
            var start = System.nanoTime()
            val s = getOpenGLObjectStateFromNode(n)
            val instances = instanceGroups.get(n)!!

            logger.trace("${n.name} has additional instance buffers: ${s.additionalBufferIds.keys}")
            logger.trace("${n.name} instancing: Instancing group size is ${instances.size}")

            val matrixSize = 4*4
            val models = ArrayList<Float>()
            val modelviews = ArrayList<Float>()
            val modelviewprojs = ArrayList<Float>()

            models.ensureCapacity(matrixSize * instances.size)
            modelviews.ensureCapacity(matrixSize * instances.size)
            modelviewprojs.ensureCapacity(matrixSize * instances.size)

            mv = GLMatrix.getIdentity()
            proj = GLMatrix.getIdentity()
            mvp = GLMatrix.getIdentity()
            var mo = GLMatrix.getIdentity()

            instances.forEachIndexed { i, node ->
                node.updateWorld(true, false)

                mo = node.model.clone()
                mv = cam.view!!.clone().mult(cam.rotation)
                mv.mult(node.world)

                proj = cam.projection!!.clone()
                mvp = proj.clone()
                mvp.mult(mv)

                models.addAll(mo.floatArray.asSequence())
                modelviews.addAll(mv.floatArray.asSequence())
                modelviewprojs.addAll(mvp.floatArray.asSequence())
            }

            val program: GLProgram? = s.program

            logger.trace("${n.name} instancing: Collected ${modelviewprojs.size/matrixSize} MVPs in ${(System.nanoTime()-start)/10e6}ms")

            program?.let {
                program.use(gl)

                program.getUniform("Material.Shinyness").setFloat(0.001f);

                if (n.material != null) {
                    program.getUniform("Material.Ka").setFloatVector(n.material!!.ambient);
                    program.getUniform("Material.Kd").setFloatVector(n.material!!.diffuse);
                    program.getUniform("Material.Ks").setFloatVector(n.material!!.specular);

                    if(n.material!!.doubleSided) {
                        gl.glDisable(GL.GL_CULL_FACE)
                    }
                } else {
                    program.getUniform("Material.Ka").setFloatVector3(n.position.toFloatBuffer());
                    program.getUniform("Material.Kd").setFloatVector3(n.position.toFloatBuffer());
                    program.getUniform("Material.Ks").setFloatVector3(n.position.toFloatBuffer());
                }

                var samplerIndex = 5;
                s.textures.forEach { type, glTexture ->
                    samplerIndex = textureTypeToUnit(type)
                    if (glTexture != null) {
                        gl.glActiveTexture(GL.GL_TEXTURE0 + samplerIndex)
                        gl.glBindTexture(GL.GL_TEXTURE_2D, glTexture.id)
                        program.getUniform("ObjectTextures[" + (samplerIndex - geometryBuffer.boundBufferNum) + "]").setInt(samplerIndex)
                    }
                    samplerIndex++
                }

                if (s.textures.size > 0) {
                    program.getUniform("materialType").setInt(1)
                }

                if (s.textures.containsKey("normal")) {
                    program.getUniform("materialType").setInt(3)
                }
            }

            if (n is HasGeometry) {
                n.preDraw()
            }

            // bind instance buffers
            start = System.nanoTime()
            val matrixSizeBytes: Long = 1L*Buffers.SIZEOF_FLOAT * matrixSize * instances.size

            gl.gL4.glBindVertexArray(s.mVertexArrayObject[0])

            gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, s.additionalBufferIds["Model"]!!);
            gl.gL4.glBufferData(GL.GL_ARRAY_BUFFER, matrixSizeBytes,
                    FloatBuffer.wrap(models.toFloatArray()), GL.GL_DYNAMIC_DRAW);

            gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, s.additionalBufferIds["ModelView"]!!);
            gl.gL4.glBufferData(GL.GL_ARRAY_BUFFER, matrixSizeBytes,
                    FloatBuffer.wrap(modelviews.toFloatArray()), GL.GL_DYNAMIC_DRAW);

            gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, s.additionalBufferIds["MVP"]!!);
            gl.gL4.glBufferData(GL.GL_ARRAY_BUFFER, matrixSizeBytes,
                    FloatBuffer.wrap(modelviewprojs.toFloatArray()), GL.GL_DYNAMIC_DRAW);

            logger.trace("${n.name} instancing: Updated matrix buffers in ${(System.nanoTime()-start)/10e6}ms")

            drawNodeInstanced(n, instances.size)
        }

        geometryBuffer.bindTexturesToUnitsWithOffset(gl, 0)
        hdrBuffer.checkAndSetDrawBuffers(gl)

        gl.glDisable(GL.GL_CULL_FACE)
        gl.glDisable(GL.GL_BLEND)
        gl.glDisable(GL.GL_DEPTH_TEST)
        gl.glPointSize(1.5f)
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE)

        gl.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)

        lightingPassProgram.bind()

        val lights = scene.discover(scene, {it is PointLight})
        lightingPassProgram.getUniform("numLights").setInt(lights.size)
        lightingPassProgram.getUniform("ProjectionMatrix").setFloatMatrix(cam.projection!!.clone(), false)
        lightingPassProgram.getUniform("InverseProjectionMatrix").setFloatMatrix(cam.projection!!.clone().invert(), false)

        for(i in 0..lights.size-1) {
            lightingPassProgram.getUniform("lights[$i].Position").setFloatVector(lights[i].position)
            lightingPassProgram.getUniform("lights[$i].Color").setFloatVector((lights[i] as PointLight).emissionColor)
            lightingPassProgram.getUniform("lights[$i].Intensity").setFloat((lights[i] as PointLight).intensity)
            lightingPassProgram.getUniform("lights[$i].Linear").setFloat((lights[i] as PointLight).linear)
            lightingPassProgram.getUniform("lights[$i].Quadratic").setFloat((lights[i] as PointLight).quadratic)
        }

        lightingPassProgram.getUniform("gPosition").setInt(0)
        lightingPassProgram.getUniform("gNormal").setInt(1)
        lightingPassProgram.getUniform("gAlbedoSpec").setInt(2)
        lightingPassProgram.getUniform("gDepth").setInt(3)

        lightingPassProgram.getUniform("debugDeferredBuffers").setInt(debugBuffers)
        lightingPassProgram.getUniform("ssao_filterRadius").setFloatVector2(10.0f/width, 10.0f/height)
        lightingPassProgram.getUniform("ssao_distanceThreshold").setFloat(5.0f)
        lightingPassProgram.getUniform("doSSAO").setInt(doSSAO)

        if(doHDR == 0) {
            geometryBuffer.revertToDefaultFramebuffer(gl)
            renderFullscreenQuad(lightingPassProgram)
        } else {
            renderFullscreenQuad(lightingPassProgram)

            hdrBuffer.bindTexturesToUnitsWithOffset(gl, 0)
            hdrBuffer.revertToDefaultFramebuffer(gl)

            hdrPassProgram.getUniform("Gamma").setFloat(this.gamma)
            hdrPassProgram.getUniform("Exposure").setFloat(this.exposure)
            renderFullscreenQuad(hdrPassProgram)
        }
    }

    fun renderFullscreenQuad(quadGenerator: GLProgram) {
       val quadId: IntBuffer = IntBuffer.allocate(1)

        quadGenerator.gl.gL4.glGenVertexArrays(1, quadId)
        quadGenerator.gl.gL4.glBindVertexArray(quadId.get(0))

        // fake call to draw one point, geometry is generated in shader pipeline
        quadGenerator.gl.gL4.glDrawArrays(GL.GL_POINTS, 0, 1)
        quadGenerator.gl.gL4.glBindTexture(GL.GL_TEXTURE_2D, 0)

        quadGenerator.gl.gL4.glBindVertexArray(0)
        quadGenerator.gl.gL4.glDeleteVertexArrays(1, quadId)
    }

    fun initializeNode(node: Node): Boolean {
        var s: OpenGLObjectState = node.metadata.get("DeferredLightingRenderer") as OpenGLObjectState

        if(node.instanceOf == null) {
            s = node.metadata.get("DeferredLightingRenderer") as OpenGLObjectState
        } else {
            s = node.instanceOf!!.metadata.get("DeferredLightingRenderer") as OpenGLObjectState
            node.metadata.set("DeferredLightingRenderer", s)

            if(!s.initialized) {
                logger.trace("Instance not yet initialized, doing now...")
                initializeNode(node.instanceOf!!)
            }

            if(!s.additionalBufferIds.containsKey("Model") || !s.additionalBufferIds.containsKey("ModelView") || !s.additionalBufferIds.containsKey("MVP")) {
                logger.trace("${node.name} triggered instance buffer creation")
                createInstanceBuffer(node.instanceOf!!)
                logger.trace("---")
            }
            return true
        }

        if(s.initialized) {
            return true
        }

        // generate VAO for attachment of VBO and indices
        gl.gL3.glGenVertexArrays(1, s.mVertexArrayObject, 0)

        // generate three VBOs for coords, normals, texcoords
        gl.glGenBuffers(3, s.mVertexBuffers, 0)
        gl.glGenBuffers(1, s.mIndexBuffer, 0)

        if (node.material == null || node.material !is OpenGLMaterial || (node.material as OpenGLMaterial).program == null) {
            if(node.useClassDerivedShader) {
                val javaClass = node.javaClass.simpleName
                val className = javaClass.substring(javaClass.indexOf(".") + 1)

                val shaders = arrayOf(".vert", ".geom", ".tese", ".tesc", ".frag", ".comp")
                        .map { "shaders/$className$it" }
                        .filter {
                            DeferredLightingRenderer::class.java.getResource(it) != null
                        }

                s.program = GLProgram.buildProgram(gl, DeferredLightingRenderer::class.java,
                        shaders.toTypedArray())
            }
            else if(node.metadata.filter { it.value is OpenGLShaderPreference }.isNotEmpty()) {
//                val prefs = node.metadata.first { it is OpenGLShaderPreference } as OpenGLShaderPreference
                val prefs = node.metadata.get("ShaderPreference") as OpenGLShaderPreference

                if(prefs.parameters.size > 0) {
                    s.program = GLProgram.buildProgram(gl, node.javaClass,
                            prefs.shaders.toTypedArray(), prefs.parameters)
                } else {
                    try {
                        s.program = GLProgram.buildProgram(gl, node.javaClass,
                                prefs.shaders.toTypedArray())
                    } catch(e: NullPointerException) {
                        s.program = GLProgram.buildProgram(gl, this.javaClass,
                                prefs.shaders.map { "shaders/" + it }.toTypedArray())
                    }

                }
            }
            else
            {
                s.program = GLProgram.buildProgram(gl, DeferredLightingRenderer::class.java,
                        arrayOf("shaders/DefaultDeferred.vert", "shaders/DefaultDeferred.frag"))
            }
        } else {
            s.program = (node.material as OpenGLMaterial).program
        }

        if(node is HasGeometry) {
            setVerticesAndCreateBufferForNode(node)
            setNormalsAndCreateBufferForNode(node)

            if (node.texcoords.size > 0) {
                setTextureCoordsAndCreateBufferForNode(node)
            }

            if (node.indices.size > 0) {
                setIndicesAndCreateBufferForNode(node)
            }
        }

        node.material?.textures?.forEach {
            type, texture ->
            if(!textures.containsKey(texture)) {
                val glTexture = GLTexture.loadFromFile(gl, texture, true, 1)
                s.textures.put(type, glTexture)
                textures.put(texture, glTexture)
            } else {
                s.textures.put(type, textures[texture]!!)
            }
        }

        s.initialized = true
        return true
    }

    private fun createInstanceBuffer(node: Node) {
        val s = getOpenGLObjectStateFromNode(node)

        val matrixSize = 4*4
        val vectorSize = 4
        val locationBase = 3
        val matrices = arrayOf("Model", "ModelView", "MVP")
        val i = IntArray(matrices.size)

        gl.gL4.glBindVertexArray(s.mVertexArrayObject[0])
        gl.gL4.glGenBuffers(matrices.size, i, 0)

        i.forEachIndexed { locationOffset, bufferId ->
            s.additionalBufferIds.put(matrices[locationOffset], bufferId)

            gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, bufferId)

            for (offset in 0..3) {
                val l = locationBase + locationOffset*vectorSize + offset

                val pointerOffsetBytes: Long = 1L * Buffers.SIZEOF_FLOAT * offset * vectorSize
                val matrixSizeBytes = matrixSize * Buffers.SIZEOF_FLOAT

                gl.gL4.glEnableVertexAttribArray(l)
                gl.gL4.glVertexAttribPointer(l, vectorSize, GL.GL_FLOAT, false,
                        matrixSizeBytes, pointerOffsetBytes)
                gl.gL4.glVertexAttribDivisor(l, 1)
            }
        }

        gl.gL4.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        gl.gL4.glBindVertexArray(0)
    }

    fun setVerticesAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pVertexBuffer: FloatBuffer = FloatBuffer.wrap((node as HasGeometry).vertices)

        s.mStoredPrimitiveCount = pVertexBuffer.remaining() / node.vertexSize

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[0])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pVertexBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pVertexBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(0,
                node.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /*fun setArbitraryAndCreateBuffer(name: String,
                                    pBuffer: FloatBuffer,
                                    pBufferGeometrySize: Int) {
        // create additional buffers
        if (!additionalBufferIds.containsKey(name)) {
            gl.glGenBuffers(1,
                    mVertexBuffers,
                    mVertexBuffers.size - 1)
            additionalBufferIds.put(name,
                    mVertexBuffers[mVertexBuffers.size - 1])
        }

        mStoredPrimitiveCount = pBuffer.remaining() / geometryObject.vertexSize

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER,
                mVertexBuffers[mVertexBuffers.size - 1])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(mVertexBuffers.size - 1,
                pBufferGeometrySize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }*/

    /*fun updateVertices(pVertexBuffer: FloatBuffer) {
        mStoredPrimitiveCount = pVertexBuffer.remaining() / geometryObject.vertexSize

        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mVertexBuffers[0])

        gl.gL3.glEnableVertexAttribArray(0)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pVertexBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pVertexBuffer,
                if (isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(0,
                geometryObject.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }*/

    fun setNormalsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pNormalBuffer: FloatBuffer = FloatBuffer.wrap((node as HasGeometry).normals)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[1])

        gl.gL3.glEnableVertexAttribArray(1)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pNormalBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(1,
                node.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /*fun updateNormals(pNormalBuffer: FloatBuffer) {
        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, mVertexBuffers[1])

        gl.gL3.glEnableVertexAttribArray(1)
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER,
                0,
                (pNormalBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pNormalBuffer)

        gl.gL3.glVertexAttribPointer(1,
                geometryObject.vertexSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }*/

    fun setTextureCoordsAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pTextureCoordsBuffer: FloatBuffer = FloatBuffer.wrap((node as HasGeometry).texcoords)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, s.mVertexBuffers[2])

        gl.gL3.glEnableVertexAttribArray(2)
        gl.glBufferData(GL.GL_ARRAY_BUFFER,
                (pTextureCoordsBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pTextureCoordsBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glVertexAttribPointer(2,
                node.texcoordSize,
                GL.GL_FLOAT,
                false,
                0,
                0)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
    }

    /*fun updateTextureCoords(pTextureCoordsBuffer: FloatBuffer) {
        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        GLError.printGLErrors(gl, "1")

        gl.gL3.glBindBuffer(GL.GL_ARRAY_BUFFER,
                mVertexBuffers[2])
        GLError.printGLErrors(gl, "2")

        gl.gL3.glEnableVertexAttribArray(2)
        GLError.printGLErrors(gl, "3")

        gl.glBufferSubData(GL.GL_ARRAY_BUFFER,
                0,
                (pTextureCoordsBuffer.limit() * (java.lang.Float.SIZE / java.lang.Byte.SIZE)).toLong(),
                pTextureCoordsBuffer)
        GLError.printGLErrors(gl, "4")

        gl.gL3.glVertexAttribPointer(2,
                geometryObject.texcoordSize,
                GL.GL_FLOAT,
                false,
                0,
                0)
        GLError.printGLErrors(gl, "5")

        gl.gL3.glBindVertexArray(0)
        GLError.printGLErrors(gl, "6")

        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        GLError.printGLErrors(gl, "7")

    }*/

    fun setIndicesAndCreateBufferForNode(node: Node) {
        val s = getOpenGLObjectStateFromNode(node);
        val pIndexBuffer: IntBuffer = IntBuffer.wrap((node as HasGeometry).indices)

        s.mStoredIndexCount = pIndexBuffer.remaining()

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, s.mIndexBuffer[0])

        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER,
                (pIndexBuffer.limit() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
                pIndexBuffer,
                if (s.isDynamic)
                    GL.GL_DYNAMIC_DRAW
                else
                    GL.GL_STATIC_DRAW)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    /*fun updateIndices(pIndexBuffer: IntBuffer) {
        if (!isDynamic)
            throw UnsupportedOperationException("Cannot update non dynamic buffers!")

        mStoredIndexCount = pIndexBuffer.remaining()

        gl.gL3.glBindVertexArray(mVertexArrayObject[0])
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, mIndexBuffer[0])

        gl.glBufferSubData(GL.GL_ELEMENT_ARRAY_BUFFER,
                0,
                (pIndexBuffer.limit() * (Integer.SIZE / java.lang.Byte.SIZE)).toLong(),
                pIndexBuffer)

        gl.gL3.glBindVertexArray(0)
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
    }*/

    fun drawNode(node: Node, offset: Int = 0) {
        val s = getOpenGLObjectStateFromNode(node);

        s.program?.use(gl)

        gl.gL3.glBindVertexArray(s.mVertexArrayObject[0])

        if (s.mStoredIndexCount > 0) {
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,
                    s.mIndexBuffer[0])
            gl.glDrawElements((node as HasGeometry).geometryType.toOpenGLType(),
                    s.mStoredIndexCount,
                    GL.GL_UNSIGNED_INT,
                    offset.toLong())

            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            gl.glDrawArrays((node as HasGeometry).geometryType.toOpenGLType(), offset, s.mStoredPrimitiveCount)
        }

        gl.gL3.glUseProgram(0)
        gl.gL4.glBindVertexArray(0)
    }

    fun drawNodeInstanced(node: Node, count: Int, offset: Long = 0) {
        val s = getOpenGLObjectStateFromNode(node);

        s.program?.use(gl)

        gl.gL4.glBindVertexArray(s.mVertexArrayObject[0])

        if(s.mStoredIndexCount > 0) {
            gl.gL4.glDrawElementsInstanced(
                    (node as HasGeometry).geometryType.toOpenGLType(),
                    s.mStoredIndexCount,
                    GL.GL_UNSIGNED_INT,
                    offset,
                    count)
        } else {
            gl.gL4.glDrawArraysInstanced(
                    (node as HasGeometry).geometryType.toOpenGLType(),
                    0, s.mStoredPrimitiveCount, count);
        }

        gl.gL4.glUseProgram(0)
        gl.gL4.glBindVertexArray(0)
    }
}