package graphics.scenery.tests.unit.network

import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.NetworkObject
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import org.junit.Test

class SubscriberTest {

    @Test
    fun simple() {
        val hub1 = Hub()
        val hub2 = Hub()

        val scene1 = Scene()
        val scene2 = Scene()

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        val sub = NodeSubscriber(hub2,"tcp://127.0.0.1:6666")
        hub2.add(sub)

        sub.debugListen(NetworkEvent.NewObject(NetworkObject(1,scene1, mutableListOf())))
        sub.debugListen(NetworkEvent.NewObject(NetworkObject(2,box, mutableListOf(scene1.networkID))))

        sub.networkUpdate(scene2)
        assert(scene2.find("box") != null)
    }
}
