package graphics.scenery.tests.unit.network

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.Sphere
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.NetworkObject
import graphics.scenery.net.NodePublisher
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals

class PublisherTest {

    @Test
    fun initialSceneDiscovery() {
        val hub = Hub()
        val pub = NodePublisher(hub)
        pub.close()

        val scene = Scene()
        val box = Box()
        val sphere = Sphere()

        scene.addChild(box)
        box.addChild(sphere)

        pub.register(scene)

        val results = mutableListOf<NetworkEvent>()

        pub.debugPublish { results.add(it) }

        val newEvents = results.filterIsInstance<NetworkEvent.NewObject>().toList()
        //val relationEvents = results.filterIsInstance<NetworkEvent.NewRelation>().toList()

        assert(newEvents.any{it.obj.obj is Scene})
        assert(newEvents.any{it.obj.obj is Box})
        assert(newEvents.any{it.obj.obj is Sphere})
        assert(newEvents.count{it.obj.obj is Material} == 2)
        assert(newEvents.count{it.obj.obj is Spatial} == 2)
    }

    @Test
    fun serializeScene() {

        val scene = Scene()
        scene.name = "lol"

        val kryo = NodePublisher.freeze()
        val bos = ByteArrayOutputStream()
        val output = Output(bos)
        kryo.writeClassAndObject(output, NetworkEvent.NewObject(NetworkObject(2, scene, mutableListOf(1))))
        output.flush()

        val bin = ByteArrayInputStream(bos.toByteArray())
        val input = Input(bin)
        val event = kryo.readClassAndObject(input) as NetworkEvent.NewObject

        assertEquals("lol", (event.obj.obj as Scene).name)
    }
}

//Inline function to access private function in the RibbonDiagram
private inline fun <reified T> T.callPrivateFunc(name: String, vararg args: Any?): Any? =
    T::class
        .declaredMemberFunctions
        .firstOrNull { it.name == name }
        ?.apply { isAccessible = true }
        ?.call(this, *args)


private fun NodePublisher.debugPublish(send: (NetworkEvent) -> Unit) =
    callPrivateFunc("debugPublish",send)
