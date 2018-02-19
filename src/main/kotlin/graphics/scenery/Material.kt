package graphics.scenery

import cleargl.GLVector
import graphics.scenery.backends.RenderConfigReader
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Material class, storing material colors, textures, opacity properties, etc.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Material : Serializable {

    enum class CullingMode { None, Front, Back, FrontAndBack }

    enum class DepthTest { Less, Greater, LessEqual, GreaterEqual, Always, Never, Equal }

    /** Name of the material. */
    var name: String = "Material"
    /** Diffuse color of the material. */
    var diffuse: GLVector = GLVector(0.9f, 0.5f, 0.5f)
    /** Specular color of the material. */
    var specular: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    /** Ambient color of the material. */
    var ambient: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    /** Specular exponent */
    var roughness: Float = 1.0f

    var metallic: Float = 0.0f

    /** Blending settings for this material. See [Blending]. */
    var blending: Blending = Blending()

    /** Hash map storing the type and origin of the material's textures. Key is the
     * type, e.g. ("diffuse", "normal", "displacement"...), value can be a file path or
     * via "fromBuffer:[transferTextureName], a named [GenericTexture] in [transferTextures]. */
    @Volatile var textures: ConcurrentHashMap<String, String> = ConcurrentHashMap()
    /** Storage for textures to be transfered to a concrete texture by the renderer. [GenericTexture]
     * stores the data and settings of the texture, a renderer will consume them later. */
    @Volatile var transferTextures: ConcurrentHashMap<String, GenericTexture> = ConcurrentHashMap()

    /** Set whether the material is double-sided */
    var doubleSided: Boolean = false

    var cullingMode: CullingMode = CullingMode.Back

    var depthTest: DepthTest = DepthTest.LessEqual

    /** Flag to check whether the [transferTextures] need reloading */
    var needsTextureReload: Boolean = false

    /** Companion object for Material, emulating static methods */
    companion object Factory {
        /**
         * Factory method returning the default material
         *
         * @return Material with default properties
         */
        fun DefaultMaterial(): Material = Material()
    }
}
