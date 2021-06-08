package graphics.scenery.controls

import java.awt.event.KeyEvent

/**
 * Class to encapsulate AWT key events.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
data class AWTKey(val code: Int,
                  val modifiers: Int = 0,
                  var time: Long = System.nanoTime(),
                  val char: Char = KeyEvent.CHAR_UNDEFINED,
                  val string: String = KeyEvent.getKeyText(code))
