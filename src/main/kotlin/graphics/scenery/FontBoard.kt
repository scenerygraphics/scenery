package graphics.scenery

import cleargl.GLVector

/**
 * FontBoard is a possibly billboarded display of a string of text,
 * rendered using signed-distance fields.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[font]  Name of the font to use for this font board
 * @property[isBillboard] Whether the board should be billboarded or not
 *
 * @constructor Returns a FontBoard instance, with [fontFamily] and a declared [ShaderMaterial]
 */
class FontBoard(font: String = "Segoe UI Light", override var isBillboard: Boolean = true) : Mesh() {

    /** The text displayed on this font board */
    var text: String = ""
        set(value) {
            dirty = true
            field = value
        }

    /** The font family of this font board. If reset, this will set the [dirty] flag,
     * such that the renderer can recreate the signed-distance fields used for displaying.
     */
    var fontFamily: String = "Segoe UI Semilight"
        set(value) {
            dirty = true
            field = value
        }

    /** The [ShaderProperty] storing the font's color. */
    @ShaderProperty var fontColor: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    /** The [ShaderProperty] storing the background color of the font board,
     * used only if [transparent] is 0. */
    @ShaderProperty var backgroundColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)
    /** The [ShaderProperty] storing whether the font board should be renderer transparently. */
    @ShaderProperty var transparent: Int = 1

    init {
        name = "FontBoard"
        fontFamily = font
        material = ShaderMaterial(arrayListOf("DefaultDeferred.vert", "FontBoard.frag"))
    }

    /** Stringify the font board. Returns [fontFamily] used as well as the [text]. */
    override fun toString(): String {
        return "FontBoard ($fontFamily): $text"
    }
}
