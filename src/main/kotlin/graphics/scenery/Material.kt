package graphics.scenery

import org.joml.Vector3f
import graphics.scenery.Material.CullingMode.*
import graphics.scenery.textures.Texture
import graphics.scenery.utils.TimestampedConcurrentHashMap
import kotlinx.serialization.Contextual

/**
 * Material class, storing material colors, textures, opacity properties, etc.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@kotlinx.serialization.Serializable
open class Material {

    /**
     * Culling Mode enum, to determine which faces are culling when assuming CCW order
     * [Front] - front faces culled
     * [Back] - back faces culled
     * [FrontAndBack] - all faces culled
     * [None] - no faces culled
     */
    @kotlinx.serialization.Serializable enum class CullingMode { None, Front, Back, FrontAndBack }

    /** Depth test enum, determines which operation on the depth buffer values results in a pass. */
    @kotlinx.serialization.Serializable enum class DepthTest { Less, Greater, LessEqual, GreaterEqual, Always, Never, Equal }

    /** Name of the material. */
    var name: String = "Material"
    /** Diffuse color of the material. */
    @Contextual var diffuse: Vector3f = Vector3f(0.9f, 0.5f, 0.5f)
    /** Specular color of the material. */
    @Contextual var specular: Vector3f = Vector3f(0.5f, 0.5f, 0.5f)
    /** Ambient color of the material. */
    @Contextual var ambient: Vector3f = Vector3f(0.5f, 0.5f, 0.5f)
    /** Specular exponent */
    var roughness: Float = 1.0f
    /** Metallicity, 0.0 is non-metal, 1.0 is full metal */
    var metallic: Float = 0.0f

    /** Blending settings for this material. See [Blending]. */
    var blending: Blending = Blending()

    /** Hash map storing the type and origin of the material's textures. Key is the
     * type, e.g. ("diffuse", "normal", "displacement"...), value can be a file path or
     * via "fromBuffer:[transferTextureName], a named [Texture] in [transferTextures]. */
    @Volatile @Contextual
    var textures: TimestampedConcurrentHashMap<String, Texture> = TimestampedConcurrentHashMap()

    /** Culling mode of the material. @see[CullingMode] */
    var cullingMode: CullingMode = CullingMode.Back

    /** depth testing mode for this material */
    var depthTest: DepthTest = DepthTest.LessEqual

    /** Flag to make the object wireframe */
    var wireframe: Boolean = false

    /** Companion object for Material, emulating static methods */
    companion object Factory {
        /**
         * Factory method returning the default material
         *
         * @return Material with default properties
         */
        @JvmStatic fun DefaultMaterial(): Material = Material()
    }

    /**
     * Returns a hash of the material, with properties relevant for
     * presentation taken into account. Does not include [textures], as
     * these are timetamped now.
     */
    fun materialHashCode() : Int {
        var result = blending.hashCode()
        result = 31 * result + cullingMode.hashCode()
        result = 31 * result + depthTest.hashCode()
        result = 31 * result + wireframe.hashCode()
        return result
    }
}
