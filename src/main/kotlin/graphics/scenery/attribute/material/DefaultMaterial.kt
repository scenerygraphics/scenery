package graphics.scenery.attribute.material

import graphics.scenery.Blending
import graphics.scenery.net.Networkable
import graphics.scenery.textures.Texture
import graphics.scenery.utils.TimestampedConcurrentHashMap
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.reflect.KClass

open class DefaultMaterial : Material, Networkable {

    override var name: String = "Material"
    override var diffuse: Vector3f = Vector3f(0.9f, 0.5f, 0.5f)
        set(value) {
            field = value
            updateModifiedAt()
        }
    override var specular: Vector3f = Vector3f(0.5f, 0.5f, 0.5f)
        set(value) {
            field = value
            updateModifiedAt()
        }
    override var ambient: Vector3f = Vector3f(0.5f, 0.5f, 0.5f)
        set(value) {
            field = value
            updateModifiedAt()
        }
    override var roughness: Float = 1.0f
        set(value) {
            field = value
            updateModifiedAt()
        }
    override var metallic: Float = 0.0f
        set(value) {
            field = value
            updateModifiedAt()
        }
    override var emissive: Vector4f = Vector4f(0.0f, 0.0f, 0.0f, 0.0f)
        set(value) {
            field = value
            updateModifiedAt()
        }
    override var blending: Blending = Blending()
    @Volatile
    @Transient
    override var textures: TimestampedConcurrentHashMap<String, Texture> = TimestampedConcurrentHashMap()
    override var cullingMode: Material.CullingMode = Material.CullingMode.Back
    override var depthTest: Boolean = true
    override var depthWrite: Boolean = true
    override var depthOp: Material.DepthTest = Material.DepthTest.LessEqual
    override var wireframe: Boolean = false
    override var wireframeWidth: Float = 1.0f
    override var timestamp: Long = System.nanoTime()
    override var modifiedAt = Long.MIN_VALUE

    var synchronizeTextures = true

    /** Companion object for Material, emulating static methods */
    companion object Factory {
        /**
         * Factory method returning the default material
         *
         * @return Material with default properties
         */
        @JvmStatic
        fun Material(): DefaultMaterial = DefaultMaterial()
    }

    override fun getAdditionalUpdateData(): Any? {
        return if (synchronizeTextures) {
            textures
        } else {
            null
        }
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        if (fresh !is DefaultMaterial) {
            throw IllegalArgumentException("Got wrong type to update ${this::class.simpleName} ")
        }
        diffuse = fresh.diffuse
        blending = fresh.blending
        (additionalData as? TimestampedConcurrentHashMap<String, Texture>)?.let {
            textures = it
        }
    }

    override fun getAttributeClass(): KClass<out Any>? {
        return Material::class
    }

    override var networkID: Int = 0
}
