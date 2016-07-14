package scenery

import cleargl.GLVector
import scenery.rendermodules.opengl.OpenGLShaderPreference
import java.util.*

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BoundingBox : Mesh() {
    var boundingCoords = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    @ShaderProperty
    var gridColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)

    @ShaderProperty
    var numLines: Int = 10

    @ShaderProperty
    var lineWidth: Float = 0.01f

    var node: Node? = null
    set(value) {
        field = value

        if(value != null) {
            val bb = value.boundingBoxCoords.clone()

            System.err.println("BB of ${value.name} is ${bb.joinToString(", ")}")

            val b = Box(GLVector(bb[1]-bb[0], bb[3]-bb[2], bb[5]-bb[4]))

            val center = GLVector((bb[1]-bb[0])/2.0f, (bb[3]-bb[2])/2.0f, (bb[5]-bb[4])/2.0f)

            this.vertices = b.vertices
            this.normals = b.normals
            this.texcoords = b.texcoords
            this.indices = b.indices

            this.boundingCoords = bb
            this.position = value.position - center
            this.scale = value.scale

            this.children[0].position = GLVector(bb[0], bb[2], bb[4])

            this.children[1].position = GLVector(bb[1], bb[2], bb[4])
            this.children[2].position = GLVector(bb[0], bb[3], bb[4])
            this.children[3].position = GLVector(bb[0], bb[2], bb[5])

            this.children.forEach {
                it.scale = this.scale
            }
            this.dirty = true

            name = "Bounding Box of ${value.name}"
        }
    }

    init {
        name = "Bounding Box"
        metadata.put(
            "ShaderPreference",
            OpenGLShaderPreference(
                arrayListOf("DefaultDeferred.vert", "BoundingBox.frag"),
                HashMap<String, String>(),
                arrayListOf("DeferredShadingRenderer")))

        val origin = FontBoard()
        origin.position = GLVector(0.0f, 0.0f, 0.0f)
        origin.fontColor = GLVector(1.0f, 1.0f, 1.0f)
        origin.backgroundColor = GLVector(0.2f, 0.2f, 0.2f)
        origin.transparent = 0
        origin.text = "origin"

        val xlabel = FontBoard()
        xlabel.fontColor = GLVector(1.0f, 1.0f, 1.0f)
        xlabel.backgroundColor = GLVector(0.2f, 0.2f, 0.2f)
        xlabel.transparent = 0
        xlabel.text = "x"

        val ylabel = FontBoard()
        ylabel.fontColor = GLVector(1.0f, 1.0f, 1.0f)
        ylabel.backgroundColor = GLVector(0.2f, 0.2f, 0.2f)
        ylabel.transparent = 0
        ylabel.text = "y"

        val zlabel = FontBoard()
        zlabel.fontColor = GLVector(1.0f, 1.0f, 1.0f)
        zlabel.backgroundColor = GLVector(0.2f, 0.2f, 0.2f)
        zlabel.transparent = 0
        zlabel.text = "z"

        this.children.add(origin)
        this.children.add(xlabel)
        this.children.add(ylabel)
        this.children.add(zlabel)
    }

    /** Stringify the font board. Returns [fontFamily] used as well as the [text]. */
    override fun toString(): String {
        return "Bounding Box of ${node?.name}, coords: ${boundingCoords.joinToString(", ")}"
    }
}
