package scenery.tests

import cleargl.GLMatrix
import cleargl.GLVector
import org.junit.Assert.assertEquals
import org.junit.Test
import scenery.Node
import scenery.Scene

/**
 * Created by ulrik on 28/01/16.
 */
class ScenegraphTests {
    @Test
    fun testTransformationPropagation() {
        val scene = Scene()

        val childOne = Node("first child")
        val subChild = Node("child of first child")

        scene.addChild(childOne)
        childOne.addChild(subChild)

        childOne.position = GLVector(1.0f, 1.0f, 1.0f)
        subChild.position = GLVector(-1.0f, 1.0f, -1.0f)

        childOne.composeModel()
        subChild.composeModel()

        System.err.println("childOne:\n${childOne.model}")
        System.err.println("subChild:\n${subChild.model}")

        childOne.updateWorld(true, force = false)
        subChild.updateWorld(true, force = false)

        System.err.println("\n-------\nAfter update:\n")

        System.err.println("childOne:\n${childOne.model}")
        System.err.println("subChild:\n${subChild.model}")

        val expectedResult = GLMatrix.getIdentity()
        expectedResult.translate(1.0f, 1.0f, 1.0f)
        expectedResult.translate(-1.0f, 1.0f, -1.0f)

        assertEquals(expectedResult, subChild.model)
    }
}