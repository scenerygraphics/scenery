package graphics.scenery

import cleargl.GLVector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
open class BoundingBox : Mesh() {
    protected var logger: Logger = LoggerFactory.getLogger("BoundingBox")

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

            value?.let {
                updateFromNode()
                value.addChild(this)

                value.updateWorld(true)
            }
        }

    fun updateFromNode() {
        node?.let { node ->
            var bb: FloatArray = node.boundingBoxCoords

            val min = GLVector(bb[0], bb[2], bb[4], 0.0f)
            val max = GLVector(bb[1], bb[3], bb[5], 0.0f)

            val b = Box(max - min)

            logger.debug("Bounding box of $node is ${bb.joinToString(", ")}")

            val center = (max - min)*0.5f

            this.vertices = b.vertices
            this.normals = b.normals
            this.texcoords = b.texcoords
            this.indices = b.indices

            this.boundingCoords = bb
            this.boundingBoxCoords = b.boundingBoxCoords
            this.position = GLVector(bb[0], bb[2], bb[4]) + center

            bb = this.boundingBoxCoords
            labels["origin"]?.position = GLVector(bb[0], bb[2], bb[4])

            labels["x"]?.position = GLVector(bb[1], bb[2], bb[4])
            labels["y"]?.position = GLVector(bb[0], bb[3], bb[4])
            labels["z"]?.position = GLVector(bb[0], bb[2], bb[5])

            node.addChild(this)
            this.needsUpdate = true
            this.needsUpdateWorld = true

            name = "Bounding Box of ${node.name}"
        }
    }

    init {
        name = "Bounding Box"
        this.material = ShaderMaterial(arrayListOf("DefaultDeferred.vert", "BoundingBox.frag"))

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

            this.addChild(fontBoard)
        }
    }

    /** Stringify the bounding box */
    override fun toString(): String {
        return "Bounding Box of ${node?.name}, coords: ${boundingCoords.joinToString(", ")}"
    }

    override fun preDraw() {
    }
}
