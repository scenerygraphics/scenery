package graphics.scenery.tests.unit.network

import graphics.scenery.DefaultNode
import graphics.scenery.attribute.spatial.DefaultSpatial
import org.joml.Vector3f
import org.junit.Test
import kotlin.test.assertEquals

class UpdateTest {

    @Test
    fun delegateSerializationAndUpdate() {

        val node = DefaultNode()
        val spatial = DefaultSpatial(node)
        spatial.position = Vector3f(3f, 0f, 0f)


        val result = serializeAndDeserialize(spatial) as DefaultSpatial

        assertEquals(3f, result.position.x)
        //should not fail
        result.position = Vector3f(3f, 0f, 0f)
    }
}
