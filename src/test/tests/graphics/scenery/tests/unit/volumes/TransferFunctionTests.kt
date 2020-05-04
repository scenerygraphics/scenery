package graphics.scenery.tests.unit.volumes

import graphics.scenery.volumes.TransferFunction
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.floor

/**
 * Tests for [TransferFunction] class.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TransferFunctionTests {
    /**
     * Tests that a flat transfer function is indeed flat.
     */
    @Test
    fun testFlatTransferFunction() {
        val tf = TransferFunction.flat()
        val buffer = tf.serialise().asFloatBuffer()

        while(buffer.hasRemaining()) {
            val value = buffer.get()
            assertEquals("All values should be equal to 1.0f", 1.0f, value)
        }
    }

    /**
     * Tests that a simple interpolated transfer function works correctly.
     */
    @Test
    fun testSimpleInterpolatedTransferFunction() {
        val tf = TransferFunction()
        tf.addControlPoint(0.5f, 0.5f)

        val buffer = tf.serialise().asFloatBuffer()
        val first = buffer.get(0)
        val last = buffer.get(tf.textureSize-1)
        val middle = buffer.get(floor(tf.textureSize/2.0f).toInt())

        assertEquals("First transfer function value should be 1.0f", 1.0f, first, 0.005f)
        assertEquals("Middle transfer function value should be 1.0f", 0.5f, middle, 0.005f)
        assertEquals("Last transfer function value should be 1.0f", 1.0f, last, 0.005f)
    }

    /**
     * Tests that a complex interpolated transfer function works correctly.
     */
    @Test
    fun testComplexInterpolatedTransferFunction() {
        val tf = TransferFunction()
        tf.addControlPoint(0.99f, 0.2f)
        tf.addControlPoint(0.5f, 0.5f)
        tf.addControlPoint(0.1f, 0.05f)
        tf.addControlPoint(0.2f, 1.0f)


        val buffer = tf.serialise().asFloatBuffer()
        val one = buffer.get((tf.textureSize*0.99f).toInt())
        val two = buffer.get((tf.textureSize*0.5f).toInt())
        val three = buffer.get((tf.textureSize*0.1f).toInt())
        val four = buffer.get((tf.textureSize*0.2f).toInt())

        val five = buffer.get((tf.textureSize*0.15f).toInt())
        val six = buffer.get((tf.textureSize*0.75f).toInt())

        assertEquals("First transfer function value should be ${tf.getControlPoint(0).factor}", tf.getControlPoint(0).factor, one, 0.02f)
        assertEquals("Second transfer function value should be ${tf.getControlPoint(1).factor}", tf.getControlPoint(1).factor, two, 0.02f)
        assertEquals("Third transfer function value should be ${tf.getControlPoint(2).factor}", tf.getControlPoint(2).factor, three, 0.02f)
        assertEquals("Fourth transfer function value should be ${tf.getControlPoint(3).factor}", tf.getControlPoint(3).factor, four, 0.02f)

        assertEquals("Function value between 3/4 should be ${(tf.getControlPoint(2).factor + tf.getControlPoint(3).factor)/2.0f}",
            (tf.getControlPoint(2).factor + tf.getControlPoint(3).factor)/2.0f, five, 0.02f)
        assertEquals("Function value between 1/2 should be ${(tf.getControlPoint(2).factor + tf.getControlPoint(3).factor)/2.0f}",
            (tf.getControlPoint(0).factor + tf.getControlPoint(1).factor)/2.0f, six, 0.02f)
    }

    @Test
    fun testTransferFunctionEvaluation() {
        val tf = TransferFunction.ramp(0.3f, 0.5f, 1.0f)

        val epsilon = 0.0001f
        assertEquals(0.0f, tf.evaluate(0.0f), epsilon)
        assertEquals(0.0f, tf.evaluate(0.1f), epsilon)
        assertEquals(0.0f, tf.evaluate(0.3f), epsilon)
        assertEquals(0.2857f, tf.evaluate(0.8f), epsilon)
        assertEquals(0.5f, tf.evaluate(1.0f), epsilon)
    }
}
