package graphics.scenery

import cleargl.GLVector

/**
 * TextBoard is a possibly billboarded display of a string of text,
 * rendered using signed-distance fields.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @property[font]  Name of the font to use for this text board
 * @property[isBillboard] Whether the board should be billboarded or not
 *
 * @constructor Returns a TextBoard instance, with [fontFamily] and a declared [ShaderMaterial]
 */
class TextBoard(font: String = "SourceSansPro-Regular.ttf", override var isBillboard: Boolean = false) : Mesh() {

    /** The text displayed on this font board */
    var text: String = ""
        set(value) {
            dirty = true
            field = value
        }

    /** The font family of this font board. If reset, this will set the [dirty] flag,
     * such that the renderer can recreate the signed-distance fields used for displaying.
     *
     * If the name contains a dot (e.g. as in "Helvetica.ttf"), scenery will attempt to load
     * the font as a file from the class path.
     */
    var fontFamily: String = "SourceSansPro-Regular.ttf"
        set(value) {
            dirty = true
            field = value
        }

    /** The [ShaderProperty] storing whether the font board should be renderer transparently. */
    @ShaderProperty var transparent: Int = 1
    /** [ShaderProperty] to store the size of the used texture atlas storing the font's signed distance field */
    @ShaderProperty var atlasSize = GLVector(1024.0f, 1024.0f, 0.0f, 0.0f)
    /** The [ShaderProperty] storing the font's color. */
    @ShaderProperty var fontColor: GLVector = GLVector(0.5f, 0.5f, 0.5f, 1.0f)
    /** The [ShaderProperty] storing the background color of the font board,
     * used only if [transparent] is 0. */
    @ShaderProperty var backgroundColor: GLVector = GLVector(1.0f, 1.0f, 1.0f, 1.0f)

    init {
        name = "TextBoard"
        fontFamily = font
        material = ShaderMaterial(arrayListOf("DefaultForward.vert", "TextBoard.frag"))
        material.blending.transparent = true
        material.blending.sourceColorBlendFactor = Blending.BlendFactor.One
        material.blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.destinationAlphaBlendFactor = Blending.BlendFactor.Zero
        material.blending.colorBlending = Blending.BlendOp.add
        material.blending.alphaBlending = Blending.BlendOp.add
    }

    /** Stringify the font board. Returns [fontFamily] used as well as the [text]. */
    override fun toString(): String {
        return "TextBoard ($fontFamily): $text"
    }
}
