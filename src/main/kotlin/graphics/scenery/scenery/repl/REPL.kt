package graphics.scenery.scenery.repl

import org.scijava.Context
import org.scijava.`object`.ObjectService
import org.scijava.ui.swing.script.InterpreterWindow
import java.util.*

/**
 * Constructs a read-eval-print loop (REPL) to interactive manipulate scenery's
 * scene rendering.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[addAccessibleObject] A list of objects that should be accessible right away in the REPL
 * @constructor Returns a REPL, equipped with a window for input/output.
 */
class REPL(vararg accessibleObjects: Any) {

    /** SciJava context for the REPL */
    protected var context: Context
    /** SciJava interpreter window, handles input and output. */
    protected var interpreterWindow: InterpreterWindow
    /** Code to evaluate upon launch. */
    protected var startupScriptCode: String = ""
    /** A startup script to evaluate upon launch. */
    protected var startupScript = "startup.js"
    /** The [startupScript] will be searched for in the resources of this class. */
    protected var startupScriptClass: Class<*> = REPL::class.java

    init {
        context = Context()
        interpreterWindow = InterpreterWindow(context)
        interpreterWindow.isVisible = false

        startupScriptCode = Scanner(startupScriptClass.getResourceAsStream(startupScript), "UTF-8").useDelimiter("\\A").next()
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
        interpreterWindow.isVisible = true
    }

    /**
     * Hides the interpreter window
     */
    fun hideConsoleWindow() {
        interpreterWindow.isVisible = false
    }

    /**
     * Launches the REPL and evaluates any set startup code.
     */
    fun start() {
        // waiting for scijava/scijava-ui-swing#22
        interpreterWindow.repl.interpreter.eval(startupScriptCode)
    }

    /**
     * Evaluate a string in the REPL
     *
     * @param[code] The code to evaluate.
     */
    fun eval(code: String) {
        interpreterWindow.repl.interpreter.eval(code)
    }
}
