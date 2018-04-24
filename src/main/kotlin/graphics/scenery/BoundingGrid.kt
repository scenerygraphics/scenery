package graphics.scenery

import cleargl.GLVector
import java.util.*

/**
 * Creating bounding boxes for [Node]s
 *
 * This class can render bounding boxes for any node, use it in the [REPL]
 * e.g. by:
 * ```
 * bb = new BoundingGrid();
 * bb.node = scene.find("ObjectName");
 * ```
 * Programmatic usage in the same way is possible of course ;)
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class BoundingGrid : Mesh("Bounding Grid") {
    var labels = HashMap<String, TextBoard>()

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
            val min = node.boundingBox?.min ?: return
            val max = node.boundingBox?.max ?: return

            val b = Box(max - min)

            logger.debug("Bounding box of $node is ${node.boundingBox}")

            val center = (max - min)*0.5f

            this.vertices = b.vertices
            this.normals = b.normals
            this.texcoords = b.texcoords
            this.indices = b.indices

            this.boundingBox = b.boundingBox
            this.position = node.boundingBox!!.min + center

            labels["origin"]?.position = this.boundingBox!!.min

            val bb = arrayListOf<Float>()
            bb.addAll(this.boundingBox!!.min.toFloatArray().toList())
            bb.addAll(this.boundingBox!!.max.toFloatArray().toList())

            labels["x"]?.position = GLVector(bb[1], bb[2], bb[4])
            labels["y"]?.position = GLVector(bb[0], bb[3], bb[4])
            labels["z"]?.position = GLVector(bb[0], bb[2], bb[5])

            node.addChild(this)
            this.needsUpdate = true
            this.needsUpdateWorld = true

            this.dirty = true

            name = "Bounding Grid of ${node.name}"
        }
    }

    init {
        this.material = ShaderMaterial(arrayListOf("DefaultForward.vert", "BoundingGrid.frag"))
        this.material.blending.transparent = true
        this.material.blending.opacity = 0.8f
        this.material.blending.setOverlayBlending()

        labels = hashMapOf(
            "origin" to TextBoard(),
            "x" to TextBoard(),
            "y" to TextBoard(),
            "z" to TextBoard()
        )

        labels.forEach { s, fontBoard ->
            fontBoard.text = s
            fontBoard.fontColor = GLVector(1.0f, 1.0f, 1.0f)
            fontBoard.backgroundColor = GLVector(0.2f, 0.2f, 0.2f)
            fontBoard.transparent = 0
            fontBoard.scale = GLVector(0.2f, 0.2f, 0.2f)

            this.addChild(fontBoard)
        }
    }

    /** Stringify the bounding box */
    override fun toString(): String {
        return "Bounding Box of ${node?.name}, coords: ${boundingBox?.min}/${boundingBox?.max}"
    }

    override fun preDraw() {
    }
}
