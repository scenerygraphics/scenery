package scenery.repl

import org.scijava.Context
import org.scijava.`object`.ObjectService
import org.scijava.service.SciJavaService
import org.scijava.ui.swing.script.InterpreterWindow
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class REPL(val accessibleObjects: List<Any> = listOf(),
           val startupScript: String = "startup.js",
           val startupScriptClass: Class<*> = REPL::class.java) {

    protected var context: Context
    protected var interpreterWindow: InterpreterWindow
    protected var startupScriptCode: String

    init {
        context = Context(SciJavaService::class.java)
        interpreterWindow = InterpreterWindow(context)
        interpreterWindow.isVisible = false

        startupScriptCode = Scanner(startupScriptClass.getResourceAsStream(startupScript), "UTF-8").useDelimiter("\\A").next()
        accessibleObjects.forEach { context.getService(ObjectService::class.java).addObject(it) }
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