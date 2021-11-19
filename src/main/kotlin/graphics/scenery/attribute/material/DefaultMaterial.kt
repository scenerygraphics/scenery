package graphics.scenery.attribute.material

import graphics.scenery.Blending
import graphics.scenery.net.Networkable
import graphics.scenery.textures.Texture
import graphics.scenery.utils.TimestampedConcurrentHashMap
import org.joml.Vector3f
import kotlin.reflect.KClass

open class DefaultMaterial : Material, Networkable {

    override var name: String = "Material"
    override var diffuse: Vector3f = Vector3f(0.9f, 0.5f, 0.5f)
    override var specular: Vector3f = Vector3f(0.5f, 0.5f, 0.5f)
    override var ambient: Vector3f = Vector3f(0.5f, 0.5f, 0.5f)
    override var roughness: Float = 1.0f
    override var metallic: Float = 0.0f
    override var blending: Blending = Blending()
    @Volatile @Transient override var textures: TimestampedConcurrentHashMap<String, Texture> = TimestampedConcurrentHashMap()
    override var cullingMode: Material.CullingMode = Material.CullingMode.Back
    override var depthTest: Material.DepthTest = Material.DepthTest.LessEqual
    override var wireframe: Boolean = false
    override var timestamp: Long = System.nanoTime()

    /** Companion object for Material, emulating static methods */
    companion object Factory {
        /**
         * Factory method returning the default material
         *
         * @return Material with default properties
         */
        @JvmStatic fun Material(): DefaultMaterial = DefaultMaterial()
    }

    override fun update(fresh: Networkable) {
        if (fresh !is DefaultMaterial){
            throw IllegalArgumentException("Got wrong type to update ${this::class.simpleName} ")
        }
        diffuse = fresh.diffuse
        blending = fresh.blending
    }

    override fun lastChange(): Long {
        //TODO("Not yet implemented")
        return Long.MIN_VALUE
    }

    override fun getAttributeClass(): KClass<out Any>? {
        return Material::class
    }

    override var networkID: Int = 0
}
