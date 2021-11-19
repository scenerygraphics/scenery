package graphics.scenery.tests.unit.network

import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import org.joml.Vector3f
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals

/**
 * Integration tests for [NodePublisher] and [NodeSubscriber]
 */
class PubSubTest {

    private lateinit var hub1: Hub
    private lateinit var hub2: Hub
    private lateinit var scene1: Scene
    private lateinit var scene2: Scene
    private lateinit var pub: NodePublisher
    private lateinit var sub: NodeSubscriber


    @Before
    fun init() {
        hub1 = Hub()
        hub2 = Hub()

        scene1 = Scene()
        scene1.name = "scene1"
        scene2 = Scene()
        scene2.name = "scene2"

        pub = NodePublisher(hub1,"tcp://127.0.0.1:6666")
        hub1.add(pub)

        sub = NodeSubscriber(hub2,"tcp://127.0.0.1:6666")
        hub2.add(sub)
    }

    @After
    fun teardown() {
        pub.close()
        sub.close()
    }

    /**
     * Instead of network use debug listen and publish
     */
    @Test
    fun integrationSkippingNetwork(){

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        pub.stopPublishing(true)

        pub.register(scene1)
        pub.debugPublish (sub::debugListen )
        sub.networkUpdate(scene2)

        assert(scene2.find("box") != null)
    }

    @Test
    fun integrationSceneName(){

        scene1.name = "lol"

        sub.startListening()
        pub.startPublishing()
        pub.register(scene1)
        Thread.sleep(2000)
        sub.networkUpdate(scene2)

        assertEquals("lol", scene2.name)
    }

    @Test
    fun integrationSimpleChildNode(){

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        sub.startListening()
        pub.startPublishing()
        pub.register(scene1)
        Thread.sleep(2000)
        sub.networkUpdate(scene2)

        assert(scene2.find("box") != null)
    }

    @Test
    fun update(){

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        sub.startListening()
        pub.startPublishing()
        pub.register(scene1)
        Thread.sleep(1000)
        sub.networkUpdate(scene2)
        box.spatial().position = Vector3f(0f,0f,3f)
        pub.scanForChanges()
        Thread.sleep(1000)
        sub.networkUpdate(scene2)

        val box2 = scene2.find("box")
        assert(box2 != null) { "precondition not met => Flaky or See previous tests" }
        assertEquals(3f, box2?.spatialOrNull()?.position?.z)
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

private fun NodePublisher.debugPublish(send: (NetworkEvent) -> Unit) =
    callPrivateFunc("debugPublish",send)
