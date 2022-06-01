package graphics.scenery.tests.unit.network

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.Box
import graphics.scenery.DefaultNode
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.net.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NodeSubscriberTest {

    @Test
    fun simple() {
        val hub2 = Hub()

        val scene1 = Scene()
        val scene2 = Scene()

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        val sub = NodeSubscriber(hub2)
        hub2.add(sub)

        sub.debugListen(NetworkEvent.Update(NetworkWrapper(1,scene1, mutableListOf())))
        sub.debugListen(NetworkEvent.Update(NetworkWrapper(2,box, mutableListOf(scene1.networkID))))

        sub.networkUpdate(scene2)
        assert(scene2.find("box") != null)
    }


    /**
     * Test the postponing of orphaned nodes until their parent is synced
     */
    @Test
    fun inverseGraphOrder() {
        val hub2 = Hub()

        val scene1 = Scene()
        scene1.name = "scene1"
        val scene2 = Scene()
        scene2.name = "scene2"

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        val sub = NodeSubscriber(hub2)
        hub2.add(sub)

        sub.debugListen(NetworkEvent.Update(NetworkWrapper(2,box, mutableListOf(1))))
        sub.debugListen(NetworkEvent.Update(NetworkWrapper(1,scene1, mutableListOf())))

        sub.networkUpdate(scene2)
        assert(scene2.find("box") != null)
    }

    @Test
    fun attributeInit() {
        val hub2 = Hub()

        val scene1 = Scene()
        val scene2 = Scene()

        val box = Box()
        box.name = "box"
        // this is the control variable
        box.spatial().position.x = 30f
        scene1.addChild(box)

        val sub = NodeSubscriber(hub2)
        hub2.add(sub)

        sub.debugListen(NetworkEvent.Update(NetworkWrapper(1,scene1, mutableListOf())))

        // simulate sending by serializing and deserializing (thereby loosing all attributes)
        val kryo = NodePublisher.freeze()
        val bos = ByteArrayOutputStream()
        val output = Output(bos)
        kryo.writeClassAndObject(output,NetworkEvent.Update(NetworkWrapper(2,box, mutableListOf(1))))
        output.flush()

        val bin = ByteArrayInputStream(bos.toByteArray())
        val input = Input(bin)
        val event = kryo.readClassAndObject(input) as NetworkEvent

        sub.debugListen(event)

        val spatialNObj = NetworkWrapper(3,box.spatial(), mutableListOf(2))
        sub.debugListen(NetworkEvent.Update(spatialNObj))

        sub.networkUpdate(scene2)
        val box2 = scene2.find("box")
        assertEquals(30f, box2?.spatialOrNull()?.position?.x)
    }

    @Test
    fun postponeUpdateAndWaitForRelatedNetworkable(){
        class UpdateNode : DefaultNode(){
            var updated = false
            override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
                getNetworkable(10) // wants other networkable with the id 10. This should fail in the first try
                updated = true
            }
        }

        val sub = NodeSubscriber(null,startNetworkActivity = false)
        val scene = Scene()
        val node = UpdateNode()

        sub.debugListen(NetworkEvent.Update(NetworkWrapper(1,scene, mutableListOf())))
        sub.debugListen(NetworkEvent.Update(NetworkWrapper(2,node, mutableListOf(1))))
        sub.networkUpdate(scene)
        assertFalse(node.updated)

        sub.debugListen(NetworkEvent.Update(NetworkWrapper(2,node, mutableListOf(1))))
        sub.networkUpdate(scene)
        assertFalse(node.updated)

        val otherNode = DefaultNode()
        sub.debugListen(NetworkEvent.Update(NetworkWrapper(10,otherNode, mutableListOf(1))))
        sub.networkUpdate(scene)
        assert(node.updated)
    }
}

