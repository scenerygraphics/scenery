package graphics.scenery

import graphics.scenery.primitives.TextBoard
import graphics.scenery.attribute.renderable.DefaultRenderable
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.attribute.material.Material
import graphics.scenery.net.Networkable
import graphics.scenery.utils.extensions.*
import org.joml.Vector3f
import org.joml.Vector4f
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
open class BoundingGrid : Mesh("Bounding Grid"), RenderingOrder {
    protected var labels = HashMap<String, TextBoard>()

    /** Grid color for the bounding grid. */
    @ShaderProperty
    var gridColor: Vector3f = Vector3f(1.0f, 1.0f, 1.0f)
        set(value) {
            field = value
            updateModifiedAt()
        }

    /** Number of lines per grid axis. */
    @ShaderProperty
    var numLines: Int = 10
        set(value) {
            field = value
            updateModifiedAt()
        }

    /** Line width for the grid. */
    @ShaderProperty
    var lineWidth: Float = 1.0f
        set(value) {
            field = value
            updateModifiedAt()
        }

    /** Whether to show only the ticks on the grid, or show the full grid. */
    @ShaderProperty
    var ticksOnly: Int = 0
        set(value) {
            field = value
            if(ticksOnly == 0) {
                material().cullingMode = Material.CullingMode.Front
            } else {
                material().cullingMode = Material.CullingMode.None
            }
            updateModifiedAt()
        }

    @ShaderProperty
    protected var boundingBoxSize: Vector3f = Vector3f(1.0f)
        set(value) {
            field = value
            updateModifiedAt()
        }


    /** Slack around transparent objects, 2% by default. */
    var slack = 0.02f

    /** The [Node] this bounding grid is attached to. Set to null to remove. */
    var node: Node? = null
        set(value) {
            updateNode(field, value)
            field = value
        }

    override var modifiedAt: Long = 0

    /** Stores the hash of the [node]'s bounding box to keep an eye on it. */
    protected var nodeBoundingBoxHash: Int = -1

    init {
        setMaterial(ShaderMaterial.fromFiles("DefaultForward.vert", "BoundingGrid.frag")) {
            blending.transparent = true
            blending.opacity = 1.0f
            blending.sourceColorBlendFactor = Blending.BlendFactor.One
            blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
            blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
            blending.destinationAlphaBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
            blending.colorBlending = Blending.BlendOp.add
            blending.alphaBlending = Blending.BlendOp.add

            if(ticksOnly > 0) {
                cullingMode = Material.CullingMode.None
            } else {
                cullingMode = Material.CullingMode.Back
            }
            depthTest = Material.DepthTest.LessEqual
        }

        labels = hashMapOf(
            "0" to TextBoard(),
            "x" to TextBoard(),
            "y" to TextBoard(),
            "z" to TextBoard()
        )

        labels.forEach { (s, fontBoard) ->
            fontBoard.text = s
            fontBoard.fontColor = Vector4f(1.0f, 1.0f, 1.0f, 1.0f)
            fontBoard.backgroundColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
            fontBoard.transparent = 1
            fontBoard.spatial {
                scale = Vector3f(0.3f, 0.3f, 0.3f)
            }

            this.addChild(fontBoard)
        }
    }

    override fun createRenderable(): Renderable {
        return object : DefaultRenderable(this) {
            override fun preDraw(): Boolean {
                super.preDraw()
                if (node?.getMaximumBoundingBox().hashCode() != nodeBoundingBoxHash) {
                    logger.debug(
                        "Updating bounding box (${
                            node?.getMaximumBoundingBox().hashCode()
                        } vs $nodeBoundingBoxHash"
                    )
                    updateNode(node, node)
                }

                return true
            }
        }
    }

    private fun updateNode(oldNode: Node?, newNode: Node?) {
        if(newNode != null) {
            oldNode?.removeChild(this)
            updateFromNode()
            newNode.addChild(this)
            newNode.spatialOrNull()?.updateWorld(true)
        }
    }

    protected fun updateFromNode() {
        node?.let { node ->
            val maxBoundingBox = node.getMaximumBoundingBox()
            nodeBoundingBoxHash = maxBoundingBox.hashCode()


            var min = maxBoundingBox.min
            var max = maxBoundingBox.max

            node.ifMaterial {
                logger.debug("Node ${node.name} is transparent: ${blending.transparent}")
                if(blending.transparent) {
                    min = min * (1.0f + slack)
                    max = max * (1.0f + slack)
                }
            }

            val b = Box(max - min)
            boundingBoxSize = max - min

            logger.debug("Bounding box of {} is {}", node, maxBoundingBox)

            val center = (max - min)*0.5f

            this.boundingBox = b.boundingBox

            val bGeometry = b.geometry()
            geometry {
                vertices = bGeometry.vertices
                normals = bGeometry.normals
                texcoords = bGeometry.texcoords
                indices = bGeometry.indices
            }
            spatial {
                position = maxBoundingBox.min + center
            }

            boundingBox?.let { bb ->
                // label coordinates are relative to the bounding box
                val hs = 2.0f * bb.halfSize

                labels["0"]?.spatial()?.position = bb.min - 0.02f * hs
                labels["x"]?.spatial()?.position = Vector3f(2.0f * bb.max.x() + 0.02f* hs.x, -0.02f * hs.y, -0.02f * hs.z) - center
                labels["y"]?.spatial()?.position = Vector3f(-0.02f * hs.x, 2.0f * bb.max.y() , 0.02f * hs.z) - center
                labels["z"]?.spatial()?.position = Vector3f(-0.02f * hs.x, -0.02f * hs.y, 2.0f * bb.max.z() + 0.02f * hs.z) - center

                val scale = Vector3f()
                this.spatial().world.getScale(scale)
                val fontScale = 0.3f
                val invScale = Vector3f(1.0f/maxOf(scale.x, 0.0001f) * fontScale,
                                        1.0f/maxOf(scale.y, 0.0001f) * fontScale,
                                        1.0f/maxOf(scale.z, 0.0001f) * fontScale)

                labels["0"]?.spatial()?.scale = invScale
                labels["x"]?.spatial()?.scale = invScale
                labels["y"]?.spatial()?.scale = invScale
                labels["z"]?.spatial()?.scale = invScale

                spatial {
                    needsUpdate = true
                    needsUpdateWorld = true
                }
                geometry {
                    dirty = true
                }

                name = "Bounding Grid of ${node.name}"
            } ?: logger.error("Bounding box of $b is null")
        }
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        super.update(fresh, getNetworkable, additionalData)
        if (fresh !is BoundingGrid) throw IllegalArgumentException("Update called with object of foreign class")
        this.gridColor = fresh.gridColor
        this.lineWidth = fresh.lineWidth
        this.numLines = fresh.numLines
        this.ticksOnly = fresh.ticksOnly
    }

    override var renderingOrder: Int = Int.MAX_VALUE

    /**
     * Returns this bounding box' coordinates and associated [Node] as String.
     */
    override fun toString(): String {
        return "Bounding Box of ${node?.name}, coords: ${boundingBox?.min}/${boundingBox?.max}"
    }
}
