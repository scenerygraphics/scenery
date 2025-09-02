package graphics.scenery.tests.unit.network

import graphics.scenery.Box
import graphics.scenery.Hub
import graphics.scenery.Scene
import graphics.scenery.*
import graphics.scenery.attribute.spatial.DefaultSpatial
import graphics.scenery.net.NetworkEvent
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Vector3f
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.zeromq.ZContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Integration tests for [NodePublisher] and [NodeSubscriber]
 */
class NodePublisherNodeSubscriberTest {

    private lateinit var hub1: Hub
    private lateinit var hub2: Hub
    private lateinit var scene1: Scene
    private lateinit var scene2: Scene
    private lateinit var pub: NodePublisher
    private lateinit var sub: NodeSubscriber
    private lateinit var zContext: ZContext

    private val sleepTime = 500L

    /**
     * Starts [NodePublisher] and [NodeSubscriber] and waits a bit to let everything setup.
     */
    @Before
    fun init() {
        Thread.sleep(300)
        hub1 = Hub()
        hub2 = Hub()

        scene1 = Scene()
        scene1.name = "scene1"
        scene2 = Scene()
        scene2.name = "scene2"

        zContext = ZContext()
        pub = NodePublisher(hub1, "tcp://127.0.0.1", 6660, context = zContext)
        hub1.add(pub)

        sub = NodeSubscriber(hub2, ip = "tcp://127.0.0.1", 6660, context = zContext)
        hub2.add(sub)

    }

    /**
     * Securely shuts down the network components and ZMQ context.
     */
    @After
    fun teardown() {
        val t = pub.close()
        sub.close().join()
        t.join()
        zContext.destroy()
    }


    @Test
    fun integrationSimpleChildNode() {

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        pub.register(scene1)
        Thread.sleep(sleepTime)
        sub.networkUpdate(scene2)

        assert(scene2.find("box") != null)
    }

    /**
     * Test updates of diffuse and position
     */
    @Test
    fun update() {

        val box = Box()
        box.name = "box"
        scene1.addChild(box)

        pub.register(scene1)
        Thread.sleep(sleepTime)
        sub.networkUpdate(scene2)

        box.spatial().position = Vector3f(0f, 0f, 3f)
        box.material().diffuse = Vector3f(0f, 0f, 3f)
        pub.scanForChanges()
        Thread.sleep(sleepTime)
        sub.networkUpdate(scene2)

        val box2 = scene2.find("box")
        val mat = box2?.materialOrNull()
        assert(box2 != null) { "precondition not met => Flaky or See previous tests" }
        assertEquals(3f, box2?.spatialOrNull()?.position?.z)
        assertEquals(3f, mat?.diffuse?.z)
    }


    @Test
    fun updatePreregisterd() {

        val box = Box().also { box ->
            box.name = "box"
            box.networkID = -2
            scene1.addChild(box)
        }
        val box2 = Box().also { box2 ->
            box2.name = "box"
            box2.networkID = -2
            scene2.addChild(box2)
        }

        pub.register(scene1)
        Thread.sleep(1000)
        sub.networkUpdate(scene2)

        box.spatial().position = Vector3f(0f, 0f, 3f)
        box.material().diffuse = Vector3f(0f, 0f, 3f)
        pub.scanForChanges()
        Thread.sleep(1000)
        sub.networkUpdate(scene2)

        val mat = box2.material()
        assertEquals(3f, box2.spatial().position.z)
        assertEquals(3f, mat.diffuse.z)
    }

    /**
     * Tests sync of transfer functions of volumes.
     */
    @Test
    fun volume() {
        class VolInt : Volume.VolumeInitializer {
            override fun initializeVolume(hub: Hub): Volume {
                return Volume.fromBuffer(emptyList(), 5, 5, 5, UnsignedByteType(), hub)
            }

        }

        val volume = Volume.forNetwork(
            VolInt(),
            hub1
        )
        volume.name = "vol"
        volume.transferFunction = TransferFunction.ramp(0.5f)
        scene1.addChild(volume)

        pub.register(scene1)
        Thread.sleep(sleepTime)
        sub.networkUpdate(scene2)

        // assert initial sync
        val testVol1 = scene2.find("vol") as? Volume
        assertNotNull(testVol1)
        assert(testVol1.dataSource !is Volume.VolumeDataSource.NullSource)
        assert(testVol1.transferFunction.serialise() == volume.transferFunction.serialise())

        // update
        volume.spatial().position = Vector3f(0f, 0f, 3f)
        volume.transferFunction = TransferFunction.ramp(0.75f)

        pub.scanForChanges()
        Thread.sleep(sleepTime)
        sub.networkUpdate(scene2)


        // assert update sync
        val testVol2 = scene2.find("vol") as? Volume
        assertNotNull(testVol2)
        assert(testVol2.transferFunction.serialise() == volume.transferFunction.serialise())
        assert(testVol2.spatial().position.z == 3f)
    }

    /**
     * Tests sync of scene names.
     */
    @Test
    fun integrationSceneName() {

        scene1.name = "lol"

        pub.register(scene1)
        Thread.sleep(sleepTime)
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

        Thread.sleep(1000)
        sub.networkUpdate(scene2)

        val parent = scene2.find("parent")
        assertNotNull(parent)
        val volume2 = parent.children.firstOrNull()
        assertNotNull(volume2)
        assertIs<Volume>(volume2)
    }
}


