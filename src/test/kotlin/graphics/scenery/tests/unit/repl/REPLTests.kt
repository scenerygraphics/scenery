package graphics.scenery.tests.unit.repl

import graphics.scenery.Hub
import graphics.scenery.backends.Renderer
import graphics.scenery.repl.REPL
import graphics.scenery.utils.lazyLogger
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Class containing tests for the [graphics.scenery.repl.REPL].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class REPLTests {
    private val logger by lazyLogger()

    /** Companion object for test setup */
    companion object {
        @JvmStatic @BeforeClass
        fun setHeadless() {
            System.setProperty(Renderer.HEADLESS_PROPERTY_NAME, "true")
        }
    }

    /** Tests the default startup script */
    @Test
    fun testStartupScript() {
        val hub = Hub()
        val repl = REPL(hub)

        val result = repl.start()
        assertNotNull(result, "Required REPL initialisation result to be non-null.")
    }

    /** Tests evaluation and return. */
    @Test
    fun testEval() {
        val hub = Hub()
        val repl = REPL(hub)

        repl.start()
        val resultAddition = repl.eval("1+2")
        assertEquals(3, resultAddition, "Required 1+2 to be 3.")

        val resultBool = repl.eval("6*7 == 42")
        assertEquals(true, resultBool, "Required 6*7 == 42 to be true.")
    }

    /** Tests object accessibility. */
    @Test
    fun testObjectAccess() {
        val hub = Hub()
        val repl = REPL(hub)

        repl.start()
        repl.addAccessibleObject(repl)

        val result = repl.eval("locate('REPL') != None")
        assertEquals(true, result, "Required REPL to be accessible from script.")
    }

}
