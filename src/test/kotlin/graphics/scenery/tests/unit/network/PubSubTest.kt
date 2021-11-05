package graphics.scenery.tests.unit.network

import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import org.junit.Test

class PubSubTest {
    @Test
    fun simple(){
        val hub1 = Hub()
        val hub2 = Hub()

        val scene1 = Scene()
        val scene2 = Scene()

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        val pub = NodePublisher(hub1,"tcp://127.0.0.1:6666")
        hub1.add(pub)

        val sub = NodeSubscriber(hub2,"tcp://127.0.0.1:6666")
        hub2.add(sub)

        pub.register(scene1)
        pub.debugPublish (sub::debugListen )
        //sub.startListening()
        //pub.startPublishing()
        Thread.sleep(2000)
        sub.networkUpdate(scene2)

        assert(scene2.find("box") != null)


    }
}
