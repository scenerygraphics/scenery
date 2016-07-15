package scenery

import cleargl.GLVector
import scenery.rendermodules.opengl.OpenGLShaderPreference
import java.util.*

/**
 * Creating bounding boxes for [Node]s
 *
 * This class can render bounding boxes for any node, use it in the [REPL]
 * e.g. by:
 * ```
 * bb = new BoundingBox();
 * bb.node = scene.find("ObjectName");
 * scene.addChild(bb);
 * ```
 * Programmatic usage in the same way is possible of course ;)
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BoundingBox : Mesh() {
    var boundingCoords = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    var labels = HashMap<String, FontBoard>()

    @ShaderProperty
    var gridColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)

    @ShaderProperty
    var numLines: Int = 10

    @ShaderProperty
    var lineWidth: Float = 1.0f

    @ShaderProperty
    var ticksOnly: Int = 0

    var node: Node? = null
    set(value) {
        field = value

        if(value != null) {
            // find largest child node
            val nodeWorldBoundingBox = {n: Node, bbcoords: FloatArray ->
                val min = GLVector(bbcoords[0], bbcoords[2], bbcoords[4], 1.0f)
                val max = GLVector(bbcoords[1], bbcoords[3], bbcoords[5], 1.0f)

                val minworld = n.world.mult(min)
                val maxworld = n.world.mult(max)

                floatArrayOf(minworld.x(), maxworld.x(), minworld.y(), maxworld.y(), minworld.z(), maxworld.z())
            }

            val boundingBoxes = NodeHelpers.discover(value, { true })
                .filter { it.boundingBoxCoords != null }
                .map { nodeWorldBoundingBox(it, it.boundingBoxCoords!!) }.toMutableList()

            if(value.boundingBoxCoords != null) {
                boundingBoxes.add(nodeWorldBoundingBox(value, value.boundingBoxCoords!!))
            }

            if(boundingBoxes.size < 1) {
                System.err.println("Could not find valid bounding boxes for ${value.name} or its children.")
                return
            }

            val bb = floatArrayOf(
                boundingBoxes.minBy { it[0] }!![0],
                boundingBoxes.maxBy { it[1] }!![1],
                boundingBoxes.minBy { it[2] }!![2],
                boundingBoxes.maxBy { it[3] }!![3],
                boundingBoxes.minBy { it[4] }!![4],
                boundingBoxes.maxBy { it[5] }!![5])

            val b = Box(GLVector(bb[1]-bb[0], bb[3]-bb[2], bb[5]-bb[4]))

            val center = GLVector((bb[1]-bb[0])/2.0f, (bb[3]-bb[2])/2.0f, (bb[5]-bb[4])/2.0f)

            this.vertices = b.vertices
            this.normals = b.normals
            this.texcoords = b.texcoords
            this.indices = b.indices

            this.boundingCoords = bb
            this.position = GLVector(bb[0], bb[2], bb[4]) + center

            labels["origin"]?.position = GLVector(bb[0], bb[2], bb[4])

            labels["x"]?.position = GLVector(bb[1], bb[2], bb[4])
            labels["y"]?.position = GLVector(bb[0], bb[3], bb[4])
            labels["z"]?.position = GLVector(bb[0], bb[2], bb[5])

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

        labels = hashMapOf(
            "origin".to(FontBoard()),
            "x".to(FontBoard()),
            "y".to(FontBoard()),
            "z".to(FontBoard())
        )

        labels.forEach { s, fontBoard ->
            fontBoard.text = s
            fontBoard.fontColor = GLVector(1.0f, 1.0f, 1.0f)
            fontBoard.backgroundColor = GLVector(0.2f, 0.2f, 0.2f)
            fontBoard.transparent = 0

            this.children.add(fontBoard)
        }
    }

    /** Stringify the bounding box */
    override fun toString(): String {
        return "Bounding Box of ${node?.name}, coords: ${boundingCoords.joinToString(", ")}"
    }
}
