package graphics.scenery.tests.unit

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion
import graphics.scenery.BufferUtils.BufferUtils.allocateFloatAndPut
import graphics.scenery.Material
import graphics.scenery.Mesh
import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.numerics.Random
import graphics.scenery.utils.LazyLogger
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer

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
        expectedResult.translate(-1.0f, -1.0f, -1.0f)
        expectedResult.rotEuler(0.5, 0.5, 0.5)
        expectedResult.scale(2.0f, 1.0f, 1.0f)

        logger.info(expectedResult.toString())

        assert(GLMatrix.compare(expectedResult, subChild.world, true))
    }

    private fun addSiblings(toNode: Node, maxSiblings: Int, currentLevel: Int, maxLevels: Int): Int {
        var totalNodes = 0
        val numSib = ThreadLocalRandom.current().nextInt(1, maxSiblings)

        (0 until numSib).map {
            if(currentLevel >= maxLevels) {
                return totalNodes
            }

            val n = Node("Sibling#$it/$currentLevel/$maxLevels")
            n.position = Random.randomVectorFromRange(3, -100.0f, 100.0f)
            n.scale = Random.randomVectorFromRange(3, 0.1f, 10.0f)
            n.rotation = Random.randomQuaternion()

            toNode.addChild(n)
            totalNodes++

            totalNodes += addSiblings(n, maxSiblings, currentLevel + 1, maxLevels)
        }

        return totalNodes
    }

    /**
     * Generates a large scene graph which should update fast and not run
     * into an overflow, while staying within a bound for the number of Nodes created.
     */
    @Test
    fun testLargeScenegraph() {
        val scene = Scene()
        val levels = 6
        val maxSiblings = 8

        val totalNodes = addSiblings(scene, maxSiblings, 0, levels)

        logger.info("Created $totalNodes nodes")

        val start = System.nanoTime()
        scene.updateWorld(true, true)
        val duration = (System.nanoTime() - start)/10e6

        assert(totalNodes <= Math.pow(1.0*maxSiblings, 1.0*levels).toInt())
        assert(totalNodes > maxSiblings)

        logger.info("Updating world for $totalNodes took $duration ms")
    }


    /**
     * Generates a large scene graph which should update fast and not run
     * into an overflow, while staying within a bound for the number of Nodes created.
     */
    @Test
    fun testLargeScenegraphDiscovery() {
        val scene = Scene()
        val levels = 6
        val maxSiblings = 8

        val totalNodes = addSiblings(scene, maxSiblings, 0, levels)

        logger.info("Created $totalNodes nodes")

        var start = System.nanoTime()
        scene.updateWorld(true, true)
        var duration = (System.nanoTime() - start)/10e6

        assert(totalNodes <= Math.pow(1.0*maxSiblings, 1.0*levels).toInt())
        assert(totalNodes > maxSiblings)

        logger.info("Updating world for $totalNodes took $duration ms")

        start = System.nanoTime()
        val discoveredNodes = scene.discover(scene, { node -> node.visible })
        duration = (System.nanoTime() - start)/10e6

        assert(totalNodes == discoveredNodes.size, { "$totalNodes nodes created, but only ${discoveredNodes.size} nodes discovered."})

        logger.info("Scene discovery for $totalNodes took $duration ms, discovered ${discoveredNodes.size} nodes")
    }

    /**
     * Tests the generation of bounding box coordinates for Meshes.
     */
    @Test
    fun testBoundingBoxGeneration() {
        val m = Mesh()

        val expectedMin = GLVector(-1.0f, -2.0f, -3.0f)
        val expectedMax = GLVector(4.0f, 5.0f, 6.0f)

        m.vertices = allocateFloatAndPut(
            floatArrayOf(
                expectedMin[0], expectedMin[1], expectedMin[2],
                expectedMax[0], expectedMax[1], expectedMax[2]))

        val expectedPosition = m.vertices.position()
        val expectedLimit = m.vertices.limit()

        assertEquals("Vertex buffer position", expectedPosition, m.vertices.position())
        assertEquals("Vertex buffer limit", expectedLimit, m.vertices.limit())

        m.boundingBox = m.generateBoundingBox()

        assertNotNull("Bounding box should not be null", m.boundingBox)

        assertArrayEquals("Bounding Box minimum",
            expectedMin.toFloatArray().toTypedArray(),
            m.boundingBox!!.min.toFloatArray().toTypedArray())
        assertArrayEquals("Bounding Box maximum",
            expectedMax.toFloatArray().toTypedArray(),
            m.boundingBox!!.max.toFloatArray().toTypedArray())
    }

    /**
     * Tests centering for Nodes.
     */
    @Test
    fun testCentering() {
        val m = Mesh()
        m.vertices = allocateFloatAndPut(
            floatArrayOf(-1.0f, -1.0f, -1.0f,
                1.0f, 1.0f, 1.0f))
        m.boundingBox = m.generateBoundingBox()

        val expectedCenter = GLVector(1.0f, 1.0f, 1.0f, 0.0f)
        val center = m.centerOn(GLVector(0.0f, 0.0f, 0.0f))
        logger.info("Centering on $center, expected=$expectedCenter")

        assertArrayEquals("Centering on $center",
            expectedCenter.toFloatArray().toTypedArray(),
            center.toFloatArray().toTypedArray())
    }

    /**
     * Tests fitting Nodes uniformly into a box of a given side length.
     */
    @Test
    fun testFitting() {
        val m = Mesh()
        m.vertices = allocateFloatAndPut(
            floatArrayOf(-1.0f, -2.0f, -4.0f,
                1.0f, 2.0f, 4.0f))
        m.boundingBox = m.generateBoundingBox()
        val scaling = m.fitInto(0.5f)
        val expectedScaling = GLVector(0.0625f, 0.0625f, 0.0625f)

        logger.info("Applied scaling: $scaling, expected=$expectedScaling")

        assertArrayEquals("Fitting into box size of side length 0.5f",
            expectedScaling.toFloatArray().toTypedArray(),
            scaling.toFloatArray().toTypedArray())
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

        node.position = Random.randomVectorFromRange(3, -100.0f, 100.0f)
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

        node.scale = Random.randomVectorFromRange(3, 0.0f, 1.0f)
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

        node.rotation = Random.randomQuaternion()
        assert(node.needsUpdate)
        assert(node.needsUpdateWorld)

        scene.updateWorld(true)

        assert(!node.needsUpdate)
        assert(!node.needsUpdateWorld)
    }

    /**
     * Tests running recursive operations on Nodes
     */
    @Test
    fun testNodeRecursion() {
        val parent = Node()
        val child1 = Node()
        val child2 = Node()
        val grandchild = Node()

        val myShinyNewMaterial = Material()

        parent.addChild(child1)
        parent.addChild(child2)

        child1.addChild(grandchild)

        parent.runRecursive({ it.material = myShinyNewMaterial })

        assertEquals("Material of parent should be $myShinyNewMaterial", myShinyNewMaterial, parent.material)
        assertEquals("Material of child1 should be $myShinyNewMaterial", myShinyNewMaterial, child1.material)
        assertEquals("Material of child2 should be $myShinyNewMaterial", myShinyNewMaterial, child2.material)
        assertEquals("Material of grandchild should be $myShinyNewMaterial", myShinyNewMaterial, grandchild.material)
    }

    /**
     * Tests running recursive operations on Nodes
     */
    @Test
    fun testNodeRecursionJavaConsumer() {
        val parent = Node()
        val child1 = Node()
        val child2 = Node()
        val grandchild = Node()

        val myShinyNewMaterial = Material()

        parent.addChild(child1)
        parent.addChild(child2)

        child1.addChild(grandchild)

        parent.runRecursive(Consumer { it.material = myShinyNewMaterial })

        assertEquals("Material of parent should be $myShinyNewMaterial", myShinyNewMaterial, parent.material)
        assertEquals("Material of child1 should be $myShinyNewMaterial", myShinyNewMaterial, child1.material)
        assertEquals("Material of child2 should be $myShinyNewMaterial", myShinyNewMaterial, child2.material)
        assertEquals("Material of grandchild should be $myShinyNewMaterial", myShinyNewMaterial, grandchild.material)
    }
}
