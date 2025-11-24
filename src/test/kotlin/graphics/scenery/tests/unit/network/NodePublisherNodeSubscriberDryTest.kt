package graphics.scenery.tests.unit.network

import graphics.scenery.*
import graphics.scenery.attribute.spatial.DefaultSpatial
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Vector3f
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.zeromq.ZContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs

/**
 * Integration tests for [NodePublisher] and [NodeSubscriber] containing test, that don't use the agent threads but debug
 * publish/listen.
 */
class NodePublisherNodeSubscriberDryTest {

    private lateinit var hub1: Hub
    private lateinit var hub2: Hub
    private lateinit var scene1: Scene
    private lateinit var scene2: Scene
    private lateinit var pub: NodePublisher
    private lateinit var sub: NodeSubscriber

    /**
     * Starts [NodePublisher] and [NodeSubscriber] and immediately cancels their worker threads. All tests now need to
     * trigger the sync by debug functions.
     */
    @Before
    fun init() {
        hub1 = Hub()
        hub2 = Hub()

        scene1 = Scene()
        scene1.name = "scene1"
        scene2 = Scene()
        scene2.name = "scene2"

        pub = NodePublisher(hub1, "tcp://127.0.0.1", 6660, context = Companion.zContext)
        hub1.add(pub)

        sub = NodeSubscriber(hub2, ip = "tcp://127.0.0.1", 6660, context = Companion.zContext)
        hub2.add(sub)

        //stop agent threads
        val p = pub.close()
        sub.close().join()
        p.join()
    }

    companion object {
        var zContext = ZContext()


        /**
         * Cleans the zcontext.
         */
        @AfterClass @JvmStatic
        fun cleanZMQ(): Unit {
            zContext.destroy()
            Thread.sleep(2000)
        }
    }

    /**
     * Instead of network use debug listen and publish to sync simple box.
     */
    @Test
    fun integrationSkippingNetwork() {

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        pub.register(scene1)
        pub.debugPublish(sub::debugListen)
        sub.networkUpdate(scene2)

        assert(scene2.find("box") != null)
    }

    /**
     * First sync then remove a node.
     */
    @Test
    fun integrationNodeRemoval() {
        val node1 = DefaultNode("eins")
        scene1.addChild(node1)
        pub.register(scene1)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene2)
        assert(scene2.find("eins") != null)

        scene1.removeChild(node1)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene1)
        assert(scene2.find("eins") == null)
    }

    /**
     * Change nodes parent and sync.
     */
    @Test
    fun integrationMoveNodeInGraph() {
        val node1 = DefaultNode("eins")
        val node2 = DefaultNode("zwei")
        scene1.addChild(node1)
        scene1.addChild(node2)
        pub.register(scene1)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene2)

        scene1.removeChild(node2)
        node1.addChild(node2)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene1)

        val zwei = scene2.find("zwei")
        assertNotNull(zwei)
        val eins = zwei.parent
        assertNotNull(eins)
    }

    /**
     * Add attribute from one node to another.
     */
    @Test
    fun integrationMoveAttribute() {

        val node1 = Box()
        node1.name = "eins"
        val node2 = Box()
        node2.name = "zwei"
        scene1.addChild(node1)
        scene1.addChild(node2)

        pub.register(scene1)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene2)

        node2.setMaterial(node1.material())
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene1)

        val eins = scene2.find("eins")
        val zwei = scene2.find("zwei")

        assert(eins?.materialOrNull() == zwei?.materialOrNull())
    }

    /**
     * Sync texture.
     */
    @Test
    fun additionalDataTexture() {
        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "box"
        box.material {
            textures["diffuse"] = Texture.fromImage(
                Image.fromResource(
                    "../../examples/basic/textures/helix.png",
                    NodePublisherNodeSubscriberDryTest::class.java
                )
            )
        }
        scene1.addChild(box)

        pub.register(scene1)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene2)

        val box2 = scene2.find("box") as? Box
        assert(box2?.material()?.textures?.isNotEmpty() ?: false)

    }

    /**
     * test serialization.
     */
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

    /**
     * Tests sync of scene names.
     */
    @Test
    fun integrationSceneName() {

        scene1.name = "lol"

        pub.register(scene1)
        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene2)

        assertEquals("lol", scene2.name)
    }

    @Test
    fun childConstructedWithParams(){
        class VolInt: Volume.VolumeInitializer{
            override fun initializeVolume(hub: Hub): Volume {
                return Volume.fromBuffer(emptyList(), 5,5,5, UnsignedByteType(), hub)
            }
        }

        pub.register(scene1)

        val volume = Volume.forNetwork(
            VolInt(),
            hub1
        )
        scene1.addChild(
            RichNode().apply {
                this.name = "parent"
                this.addChild(volume)
            }
        )

        pub.debugPublish { sub.debugListen(serializeAndDeserialize(it) as NetworkEvent) }
        sub.networkUpdate(scene2)

        val parent = scene2.find("parent")
        assertNotNull(parent)
        val volume2 = parent.children.firstOrNull()
        assertNotNull(volume2)
        assertIs<Volume>(volume2)
    }
}


