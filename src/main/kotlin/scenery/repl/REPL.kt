package scenery.repl

import org.scijava.Context
import org.scijava.`object`.ObjectService
import org.scijava.ui.swing.script.InterpreterWindow
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class REPL(vararg accessibleObjects: Any) {

    protected var context: Context
    protected var interpreterWindow: InterpreterWindow
    protected var startupScriptCode: String = ""
    protected var startupScript = "startup.js"
    protected var startupScriptClass: Class<*> = REPL::class.java

    init {
        context = Context()
        interpreterWindow = InterpreterWindow(context)
        interpreterWindow.isVisible = false

        startupScriptCode = Scanner(startupScriptClass.getResourceAsStream(startupScript), "UTF-8").useDelimiter("\\A").next()
        accessibleObjects.forEach { context.getService(ObjectService::class.java).addObject(it) }
    }

    fun setStartupScript(scriptFileName: String, baseClass: Class<*>) {
        startupScriptClass = baseClass
        startupScript = scriptFileName

        startupScriptCode = Scanner(startupScriptClass.getResourceAsStream(startupScript), "UTF-8").useDelimiter("\\A").next()
    }

    fun addAccessibleObject(obj: Any) {
        context.getService(ObjectService::class.java).addObject(obj)
    }

    fun showConsoleWindow() {
        interpreterWindow.isVisible = true
    }

    fun hideConsoleWindow() {
        interpreterWindow.isVisible = false
    }

    fun start() {
        // waiting for scijava/scijava-ui-swing#22
        interpreterWindow.repl.interpreter.eval(startupScriptCode)
    }

     fun eval(code: String) {
         interpreterWindow.repl.interpreter.eval(code)
     }
}
