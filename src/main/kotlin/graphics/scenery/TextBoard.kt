package graphics.scenery

import cleargl.GLVector
import graphics.scenery.fonts.SDFFontAtlas

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
            if(value != field) {
                field = value
                updateBoard(atlas, value)

                dirty = true
            }
        }

    /** The font family of this font board. If reset, this will set the [dirty] flag,
     * such that the renderer can recreate the signed-distance fields used for displaying.
     *
     * If the name contains a dot (e.g. as in "Helvetica.ttf"), scenery will attempt to load
     * the font as a file from the class path.
     */
    var fontFamily: String = font
        set(value) {
            if(value != field) {
                field = value
                atlas = updateAtlas(value)
                updateBoard(atlas, text)

                dirty = true
            }
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

    /** Temporary storage for the current atlas */
    private var atlas: SDFFontAtlas

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
        material.cullingMode = Material.CullingMode.None

        atlas = updateAtlas(fontFamily)
        updateBoard(atlas, text)
    }

    private fun updateAtlas(newFontFamily: String): SDFFontAtlas {
        logger.debug("Updating SDF font atlas for {}, new font: {}", name, fontFamily)
        return sdfCache.getOrPut(newFontFamily,
            { SDFFontAtlas(Hub.getDefaultHub(), newFontFamily,
                maxDistance = Hub.getDefaultHub().get<Settings>(SceneryElement.Settings)!!.get("sdf.MaxDistance")) })
    }

    private fun updateBoard(a: SDFFontAtlas, newText: String) {
        logger.debug("Updating mesh for text board {} to '{}'...", name, newText)
        val m = a.createMeshForString(newText)

        vertices = m.vertices
        normals = m.normals
        indices = m.indices
        texcoords = m.texcoords
        atlasSize = GLVector(atlas.atlasWidth.toFloat(), atlas.atlasHeight.toFloat(), 0.0f, 0.0f)

        material.textures["diffuse"] = "fromBuffer:diffuse"
        material.transferTextures["diffuse"] = GenericTexture("diffuse",
            GLVector(atlasSize.x(), atlasSize.y(), 1.0f),
            channels = 1, contents = atlas.getAtlas(),
            repeatS = false, repeatT = false,
            normalized = true,
            mipmap = true)

        dirty = true
    }

    /** Stringify the font board. Returns [fontFamily] used as well as the [text]. */
    override fun toString(): String {
        return "TextBoard ($fontFamily): $text"
    }

    companion object {
        val sdfCache = HashMap<String, SDFFontAtlas>()
    }
}
