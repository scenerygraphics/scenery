package graphics.scenery

import cleargl.GLVector
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Material class, storing material colors, textures, opacity properties, etc.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Material : Serializable {
    /** Name of the material. */
    var name: String = "Material"
    /** Diffuse color of the material. */
    var diffuse: GLVector = GLVector(0.9f, 0.5f, 0.5f)
    /** Specular color of the material. */
    var specular: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    /** Ambient color of the material. */
    var ambient: GLVector = GLVector(0.5f, 0.5f, 0.5f)

    /** Opacity of the material. */
    var opacity = 1.0f
    /** Set whether this material should be transparent, transparency degree can be set via [opacity]. */
    var transparent: Boolean = false
    /** Specular exponent */
    var specularExponent: Float = 0.0f

    /** Hash map storing the type and origin of the material's textures. Key is the
     * type, e.g. ("diffuse", "normal", "displacement"...), value can be a file path or
     * via "fromBuffer:[transferTextureName], a named [GenericTexture] in [transferTextures]. */
    @Volatile var textures: ConcurrentHashMap<String, String> = ConcurrentHashMap()
    /** Storage for textures to be transfered to a concrete texture by the renderer. [GenericTexture]
     * stores the data and settings of the texture, a renderer will consume them later. */
    @Volatile var transferTextures: ConcurrentHashMap<String, GenericTexture> = ConcurrentHashMap()

    /** Set whether the material is double-sided */
    var doubleSided: Boolean = false

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
