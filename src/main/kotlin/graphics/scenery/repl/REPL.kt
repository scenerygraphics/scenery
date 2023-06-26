package graphics.scenery.repl

import graphics.scenery.Hub
import graphics.scenery.Hubable
import net.imagej.lut.LUTService
import org.scijava.Context
import org.scijava.`object`.ObjectService
import org.scijava.script.ScriptREPL
import org.scijava.ui.swing.script.InterpreterWindow
import java.util.*
import javax.swing.SwingUtilities

/**
 * Constructs a read-eval-print loop (REPL) to interactive manipulate scenery's
 * scene rendering.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[addAccessibleObject] A list of objects that should be accessible right away in the REPL
 * @constructor Returns a REPL, equipped with a window for input/output.
 */
class REPL @JvmOverloads constructor(override var hub : Hub?, scijavaContext: Context? = null, vararg accessibleObjects: Any) : Hubable {

    /** SciJava context for the REPL */
    protected var context: Context
    /** SciJava interpreter window, handles input and output. */
    protected var interpreterWindow: InterpreterWindow? = null
    /** SciJava REPL **/
    protected var repl: ScriptREPL? = null
    /** Code to evaluate upon launch. */
    protected var startupScriptCode: String = ""
    /** A startup script to evaluate upon launch. */
    protected var startupScript = "startup.py"
    /** The [startupScript] will be searched for in the resources of this class. */
    protected var startupScriptClass: Class<*> = REPL::class.java
    /** Whether we are running headless or not */
    protected val headless = (System.getProperty("scenery.Headless", "false")?.toBoolean() ?: false) || (System.getProperty("java.awt.headless", "false")?.toBoolean() ?: false)

    /** Language preference for the REPL */
    protected val languagePreference = "Python (Jython)"

    init {
        hub?.add(this)

        context = scijavaContext ?: Context(ObjectService::class.java, LUTService::class.java)

        if(!headless) {
            interpreterWindow = InterpreterWindow(context, languagePreference)
            interpreterWindow?.isVisible = false
            repl = interpreterWindow?.repl
        } else {
            repl = ScriptREPL(context, languagePreference, System.out)
            repl?.initialize()
        }

        setStartupScript(startupScript, startupScriptClass)
        accessibleObjects.forEach { context.getService(ObjectService::class.java).addObject(it) }
    }

    /**
     * Sets a startup script and its class to find it in its resources.
     *
     * @param[scriptFileName] The file name of the script
     * @param[baseClass] The class whose resources to search for the script
     */
    fun setStartupScript(scriptFileName: String, baseClass: Class<*>) {
        startupScriptClass = baseClass
        startupScript = scriptFileName

        startupScriptCode = Scanner(startupScriptClass.getResourceAsStream(startupScript), "UTF-8").useDelimiter("\\A").next()
    }

    /**
     * Adds an object to the REPL's accessible objects
     *
     * @param[obj] The object to add.
     */
    fun addAccessibleObject(obj: Any) {
        context.getService(ObjectService::class.java).addObject(obj)
    }

    /**
     * Shows the interpreter window
     */
    fun showConsoleWindow() {
        interpreterWindow?.isVisible = true
    }

    /**
     * Hides the interpreter window
     */
    fun hideConsoleWindow() {
        interpreterWindow?.isVisible = false
    }

    /**
     * Launches the REPL and evaluates any set startup code.
     */
    fun start() {
        repl?.lang("Python (Jython)")
        eval(startupScriptCode)
    }

    /**
     * Evaluate a string in the REPL
     *
     * @param[code] The code to evaluate.
     */
    fun eval(code: String): Any? {
        return repl?.interpreter?.eval(code)
    }

    /**
     * Closes the REPL instance.
     */
    fun close() {
        if(!headless) {
            SwingUtilities.invokeAndWait {
                interpreterWindow?.isVisible = false
                interpreterWindow?.dispose()
            }
        }
    }
}
