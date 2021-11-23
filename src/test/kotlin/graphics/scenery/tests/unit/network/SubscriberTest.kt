package graphics.scenery.tests.unit.network

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.NetworkObject
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals

class SubscriberTest {

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

        sub.debugListen(NetworkEvent.Update(NetworkObject(1,scene1, mutableListOf())))
        sub.debugListen(NetworkEvent.Update(NetworkObject(2,box, mutableListOf(scene1.networkID))))

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
        val scene2 = Scene()

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        val sub = NodeSubscriber(hub2)
        hub2.add(sub)

        sub.debugListen(NetworkEvent.Update(NetworkObject(2,box, mutableListOf(1))))
        sub.debugListen(NetworkEvent.Update(NetworkObject(1,scene1, mutableListOf())))

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

        sub.debugListen(NetworkEvent.Update(NetworkObject(1,scene1, mutableListOf())))

        // simulate sending by serializing and deserializing (thereby loosing all attributes)
        val kryo = NodePublisher.freeze()
        val bos = ByteArrayOutputStream()
        val output = Output(bos)
        kryo.writeClassAndObject(output,NetworkEvent.Update(NetworkObject(2,box, mutableListOf(1))))
        output.flush()

        val bin = ByteArrayInputStream(bos.toByteArray())
        val input = Input(bin)
        val event = kryo.readClassAndObject(input) as NetworkEvent

        sub.debugListen(event)

        val spatialNObj = NetworkObject(3,box.spatial(), mutableListOf(2))
        sub.debugListen(NetworkEvent.Update(spatialNObj))

        sub.networkUpdate(scene2)
        val box2 = scene2.find("box")
        assertEquals(30f, box2?.spatialOrNull()?.position?.x)
    }


}

//Inline function to access private function in the RibbonDiagram
private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
    T::class
        .declaredMemberFunctions
        .firstOrNull { it.name == name }
        ?.apply { isAccessible = true }
        ?.call(this, *args)

private fun NodeSubscriber.debugListen(event: NetworkEvent) =
    callPrivateFunc("debugListen",event)

