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
    var lineWidth: Float = 1.2f

    @ShaderProperty
    var ticksOnly: Int = 1

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
            val min = node.getMaximumBoundingBox().min
            val max = node.getMaximumBoundingBox().max

            val b = Box(max - min)

            logger.debug("Bounding box of $node is ${node.getMaximumBoundingBox()}")

            val center = (max - min)*0.5f

            this.vertices = b.vertices
            this.normals = b.normals
            this.texcoords = b.texcoords
            this.indices = b.indices

            this.boundingBox = b.boundingBox
            this.position = node.getMaximumBoundingBox().min + center

            boundingBox?.let { bb ->
                // label coordinates are relative to the bounding box
                labels["0"]?.position = bb.min - GLVector(0.1f, 0.0f, 0.0f)
                labels["x"]?.position = GLVector(2.0f * bb.max.x() + 0.1f, 0.01f, 0.01f) - center
                labels["y"]?.position = GLVector(-0.1f, 2.0f * bb.max.y(), 0.01f) - center
                labels["z"]?.position = GLVector(-0.1f, 0.01f, 2.0f * bb.max.z()) - center

                node.addChild(this)
                this.needsUpdate = true
                this.needsUpdateWorld = true

                this.dirty = true

                name = "Bounding Grid of ${node.name}"
            } ?: logger.error("Bounding box of $b is null")
        }
    }

    init {
        material = ShaderMaterial(arrayListOf("DefaultForward.vert", "BoundingGrid.frag"))
        material.blending.transparent = true
        material.blending.opacity = 0.8f
        material.blending.setOverlayBlending()
        material.cullingMode = Material.CullingMode.Front


        labels = hashMapOf(
            "0" to TextBoard(),
            "x" to TextBoard(),
            "y" to TextBoard(),
            "z" to TextBoard()
        )

        labels.forEach { s, fontBoard ->
            fontBoard.text = s
            fontBoard.fontColor = GLVector(1.0f, 1.0f, 1.0f)
            fontBoard.backgroundColor = GLVector(0.0f, 0.0f, 0.0f)
            fontBoard.transparent = 1
            fontBoard.scale = GLVector(0.3f, 0.3f, 0.3f)

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
