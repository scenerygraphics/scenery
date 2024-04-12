package graphics.scenery.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.ShaderMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Shaders

class ShaderMaterialSerializer: Serializer<ShaderMaterial>() {
    /** Writes the bytes for the object to the output.
     *
     *
     * This method should not be called directly, instead this serializer can be passed to [Kryo] write methods that accept a
     * serialier.
     * @param object May be null if [.getAcceptsNull] is true.
     */
    override fun write(kryo: Kryo, output: Output, material: ShaderMaterial) {
        kryo.writeClass(output, ShaderMaterial::class.java)
        output.writeBoolean(material.isCompute())
        kryo.writeClassAndObject(output, material as Material)
        kryo.writeClassAndObject(output, material.shaders)
    }

    /** Reads bytes and returns a new object of the specified concrete type.
     *
     *
     * Before Kryo can be used to read child objects, [Kryo.reference] must be called with the parent object to
     * ensure it can be referenced by the child objects. Any serializer that uses [Kryo] to read a child object may need to
     * be reentrant.
     *
     *
     * This method should not be called directly, instead this serializer can be passed to [Kryo] read methods that accept a
     * serialier.
     * @return May be null if [.getAcceptsNull] is true.
     */
    override fun read(kryo: Kryo, input: Input, type: Class<out ShaderMaterial>): ShaderMaterial {
        val clz = kryo.readClass(input)
        val isCompute = input.readBoolean()
        val obj = kryo.readClassAndObject(input) as Material
        val shaders = kryo.readClassAndObject(input) as Shaders

        val sm = ShaderMaterial(shaders)
        sm.textures = obj.textures
        sm.ambient = obj.ambient
        sm.blending = obj.blending
        sm.depthOp = obj.depthOp
        sm.depthTest = obj.depthTest
        sm.depthWrite = obj.depthWrite
        sm.metallic = obj.metallic
        sm.name = obj.name
        sm.roughness = obj.roughness

        return sm
    }
}
