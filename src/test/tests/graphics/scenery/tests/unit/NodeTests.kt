package graphics.scenery.tests.unit

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.BufferUtils
import graphics.scenery.Mesh
import org.junit.Test
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Numerics
import java.util.concurrent.ThreadLocalRandom

/**
 * Tests for functions of [Node]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class NodeTests {
    private val logger by LazyLogger()

    /**
     * Tests matrix propagation through the scene graph.
     */
    @Test
    fun testTransformationPropagation() {
        val scene = Scene()

        val childOne = Node("first child")
        val subChild = Node("child of first child")

        scene.addChild(childOne)
        childOne.addChild(subChild)

        childOne.position = GLVector(1.0f, 1.0f, 1.0f)
        subChild.position = GLVector(-1.0f, -1.0f, -1.0f)
        subChild.scale = GLVector(2.0f, 1.0f, 1.0f)
        subChild.rotation = Quaternion().setFromEuler(0.5f, 0.5f, 0.5f)

        logger.info("childOne:\n${childOne.world}")
        logger.info("subChild:\n${subChild.world}")

        childOne.updateWorld(true, force = false)
        //subChild.updateWorld(true, force = false)

        logger.info("\n-------\nAfter update:\n")

        logger.info("childOne:\n${childOne.world}")
        logger.info("subChild:\n${subChild.world}")

        val expectedResult = GLMatrix.getIdentity()
        expectedResult.translate(1.0f, 1.0f, 1.0f)
        expectedResult.scale(2.0f, 1.0f, 1.0f)
        expectedResult.rotEuler(0.5, 0.5, 0.5)
        expectedResult.translate(-1.0f, -1.0f, -1.0f)

        logger.info(expectedResult.toString())

        assert(GLMatrix.compare(expectedResult, subChild.world, true))
    }

    /**
     * Generates a large scene graph which should update fast and not run
     * into an overflow, while staying within a bound for the number of Nodes created.
     */
    @Test
    fun testLargeScenegraph() {
        val scene = Scene()
        val levels = 8
        val maxSiblings = 8

        var totalNodes = 0

        fun addSiblings(toNode: Node, maxSiblings: Int, currentLevel: Int) {
            val numSib = ThreadLocalRandom.current().nextInt(1, maxSiblings)

            (0 until numSib).map {
                if(currentLevel >= levels) {
                    return
                }

                val n = Node("Sibling#$it")
                n.position = Numerics.randomVectorFromRange(3, -100.0f, 100.0f)
                n.scale = Numerics.randomVectorFromRange(3, 0.1f, 10.0f)
                n.rotation = Numerics.randomQuaternion()

                toNode.addChild(n)
                totalNodes++

                addSiblings(n, maxSiblings, currentLevel + 1)
            }
        }

        addSiblings(scene, maxSiblings, 0)

        logger.info("Created $totalNodes nodes")

        val start = System.nanoTime()
        scene.updateWorld(true, true)
        val duration = (System.nanoTime() - start)/10e6

        assert(totalNodes <= Math.pow(1.0*maxSiblings, 1.0*levels).toInt())
        assert(totalNodes > maxSiblings)

        logger.info("Updating world for $totalNodes took $duration ms")
    }

    /**
     * Tests the generation of bounding box coordinates for Meshes.
     */
    @Test
    fun testBoundingBoxGeneration() {
        val m = Mesh()
        m.vertices = BufferUtils.allocateFloatAndPut(
            floatArrayOf(-1.0f, -1.0f, -1.0f,
                1.0f, 1.0f, 1.0f))

        val expectedResult = floatArrayOf(-1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f)
        val expectedPosition = m.vertices.position()
        val expectedLimit = m.vertices.limit()

        assert(m.vertices.position() == expectedPosition)
        assert(m.vertices.limit() == expectedLimit)
        assert(m.generateBoundingBox() contentEquals expectedResult)
    }

    /**
     * Tests centering for Nodes.
     */
    @Test
    fun testCentering() {
        val m = Mesh()
        m.vertices = BufferUtils.allocateFloatAndPut(
            floatArrayOf(-1.0f, -1.0f, -1.0f,
                1.0f, 1.0f, 1.0f))
        m.generateBoundingBox()

        val expectedCenter = GLVector(1.0f, 1.0f, 1.0f, 0.0f)
        val center = m.centerOn(GLVector(0.0f, 0.0f, 0.0f))
        logger.info("Centering on $center, expected=$expectedCenter")
        assert(center.toFloatArray() contentEquals  expectedCenter.toFloatArray())
    }

    /**
     * Tests fitting Nodes uniformly into a box of a given side length.
     */
    @Test
    fun testFitting() {
        val m = Mesh()
        m.vertices = BufferUtils.allocateFloatAndPut(
            floatArrayOf(-1.0f, -2.0f, -4.0f,
                1.0f, 2.0f, 4.0f))
        m.generateBoundingBox()
        val scaling = m.fitInto(0.5f)
        val expectedScaling = GLVector(0.0625f, 0.0625f, 0.0625f)

        logger.info("Applied scaling: $scaling, expected=$expectedScaling")

        assert(scaling.toFloatArray() contentEquals expectedScaling.toFloatArray())
    }

    /**
     * Tests getting to the root scene object from connected and unconnected Nodes.
     */
    @Test
    fun testGetScene() {
        val scene = Scene()
        val n1 = Node()
        val n2 = Node()
        scene.addChild(n1)

        assert(n1.getScene() == scene)
        assert(n2.getScene() == null)
    }

    /**
     * Tests triggering model/world updates after setting the position of the Node.
     */
    @Test
    fun testPositionChangeTriggersUpdate() {
        val scene = Scene()
        val node = Node()
        scene.addChild(node)

        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        node.position = Numerics.randomVectorFromRange(3, -100.0f, 100.0f)
        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        scene.updateWorld(true)

        assert(!node.needsUpdate)
        assert(!node.needsUpdateWorld)
    }

    /**
     * Tests triggering model/world updates after setting the scale of the Node.
     */
    @Test
    fun testScaleChangeTriggersUpdate() {
        val scene = Scene()
        val node = Node()
        scene.addChild(node)

        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        node.scale = Numerics.randomVectorFromRange(3, 0.0f, 1.0f)
        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        scene.updateWorld(true)

        assert(!node.needsUpdate)
        assert(!node.needsUpdateWorld)
    }

    /**
     * Tests triggering model/world updates after setting the rotation of the Node.
     */
    @Test
    fun testRotationChangeTriggersUpdate() {
        val scene = Scene()
        val node = Node()
        scene.addChild(node)

        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        node.rotation = Numerics.randomQuaternion()
        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        scene.updateWorld(true)

        assert(!node.needsUpdate)
        assert(!node.needsUpdateWorld)
    }
}
