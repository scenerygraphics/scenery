package graphics.scenery.tests.unit.network

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.DefaultNode
import graphics.scenery.attribute.spatial.DefaultSpatial
import graphics.scenery.net.NodePublisher
import org.joml.Vector3f
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class UpdateTest {

    @Test
    fun delegateSerializationAndUpdate(){

        val node = DefaultNode()
        val spatial = DefaultSpatial(node)
        spatial.position = Vector3f(3f,0f,0f)


        val kryo = NodePublisher.freeze()
        val bos = ByteArrayOutputStream()
        val output = Output(bos)
        kryo.writeClassAndObject(output, spatial)
        output.flush()

        val bin = ByteArrayInputStream(bos.toByteArray())
        val input = Input(bin)
        val result = kryo.readClassAndObject(input) as DefaultSpatial

        assertEquals(3f,result.position.x)
        //should not fail
        result.position = Vector3f(3f,0f,0f)
    }
}
