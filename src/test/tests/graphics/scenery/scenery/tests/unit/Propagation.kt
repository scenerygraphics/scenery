package graphics.scenery.scenery.tests.unit

import cleargl.GLMatrix
import cleargl.GLVector
import org.junit.Test
import graphics.scenery.scenery.Node
import graphics.scenery.scenery.Scene

/**
 * Created by ulrik on 28/01/16.
 */
class Propagation {
    @Test
    fun testTransformationPropagation() {
        val scene = Scene()

        val childOne = Node("first child")
        val subChild = Node("child of first child")

        scene.addChild(childOne)
        childOne.addChild(subChild)

        childOne.position = GLVector(1.0f, 1.0f, 1.0f)
        subChild.position = GLVector(-1.0f, -1.0f, -1.0f)

        System.err.println("childOne:\n${childOne.world}")
        System.err.println("subChild:\n${subChild.world}")

        childOne.updateWorld(true, force = false)
        //subChild.updateWorld(true, force = false)

        System.err.println("\n-------\nAfter update:\n")

        System.err.println("childOne:\n${childOne.world}")
        System.err.println("subChild:\n${subChild.world}")

        val expectedResult = GLMatrix.getIdentity()
        expectedResult.translate(1.0f, 1.0f, 1.0f)
        expectedResult.translate(-1.0f, -1.0f, -1.0f)

        System.err.println(expectedResult)

        assert(GLMatrix.compare(expectedResult, subChild.world, true))
    }


//    @Test
//    fun testLargeScenegraph() {
//        val scene = Scene()
//        val nodeList = ArrayList<Node>()
//        val levels = 10
//        val maxSiblings = 10
//
//        var start: Long
//        var stop: Long
//
//        var currentNode: Node
//
//        for(level in 1..levels) {
//            if(level == 1) {
//                currentNode = scene
//            }
//
//            var siblings = ArrayList<Node>()
//            var numSib = ThreadLocalRandom.current().nextInt(0, maxSiblings)
//
//            (1..numSib).map {
//                val n = Node("Sibling#"+it)
//                siblings.add(n)
//            }
//        }
//
//        scene.addChild(nodeList[0])
//
//        System.out.println("Created ${nodeList.size} nodes")
//        start = System.nanoTime()
//
//        nodeList.map {
//            val child = nodeList.get(ThreadLocalRandom.current().nextInt(0, size))
//            if(it !== child) {
//                it.addChild(child)
//                System.out.println("Linking ${it} to ${child}")
//            }
//        }
//
//        (1..multiplicity*size).map {
//            val node = nodeList.get(ThreadLocalRandom.current().nextInt(0, size))
//            val child = nodeList.get(ThreadLocalRandom.current().nextInt(0, size))
//
//            if(node !== child) {
//                node.addChild(child)
//                System.out.println("Linking ${node} to ${child}")
//            }
//        }
//
//        stop = System.nanoTime()
//        System.out.println("Interlinking took ${(stop-start)/1e6} ms")
//
//        start = System.nanoTime()
//        nodeList[0].updateWorld(true)
//        stop = System.nanoTime()
//
//        System.out.println("Updating world took ${(stop-start)/1e6} ms")
//    }
}
