package graphics.scenery.controls.behaviours

import graphics.scenery.Node
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.StarTree
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.utils.LazyLogger
import graphics.scenery.utils.Wiggler
import graphics.scenery.utils.extensions.minus
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.DragBehaviour

/**
 * A selection wheel to let the user choose between different actions.
 *
 * The list of selectable actions can be changed dynamically.
 *
 * @param actions List of named lambdas which can be selected by the user
 * @param cutoff  after this distance between controller and targets no action will be selected if the button is released
 */
open class VRSelectionStarTree(
    val controller: Spatial,
    val scene: Scene,
    val getHmdPos: () -> Vector3f,
    val starTree: StarTree,
    val cutoff: Float = 0.1f
) : DragBehaviour {
    protected val logger by LazyLogger(System.getProperty("scenery.LogLevel", "info"))
    private var activeWiggler: Wiggler? = null
    private var currentStarTree: StarTree = starTree

    override fun init(x: Int, y: Int) {
        starTree.spatial {
            position = controller.worldPosition()
        }
        starTree.update.add {
            starTree.spatial {
                val hmdp = getHmdPos()
                val diff = (hmdp - position).normalize()
                rotation = Quaternionf().rotationTo(Vector3f(0f, 0f, 1f), diff)
            }
        }
        scene.addChild(starTree)
    }

    override fun drag(x: Int, y: Int) {

        val closestStarTree = closestStarTree()

        if (closestStarTree.distance > cutoff || closestStarTree.starTree.root) {
            activeWiggler?.deativate()
            activeWiggler = null

        } else if (activeWiggler?.target != closestStarTree.starTree.children.filter { it.name == "StarSphere" }[0].ifSpatial {  }) {
            activeWiggler?.deativate()
            activeWiggler = Wiggler(closestStarTree.starTree.children.filter { it.name == "StarSphere" }[0].ifSpatial {  }!!, 0.01f)

            if(currentStarTree != closestStarTree.starTree) {
                currentStarTree.hideChildren()
                currentStarTree = closestStarTree.starTree
                currentStarTree.showChildren()
            }
        }

    }

    override fun end(x: Int, y: Int) {
        val closestStarTree = closestStarTree()

        if (closestStarTree.distance < cutoff) {
            closestStarTree.starTree.action()
        }

        activeWiggler?.deativate()
        activeWiggler = null

        scene.removeChild(starTree)
    }

    /**
     * @return (closest actionSphere) to (distance to controller)
     */
    private fun closestStarTree(): StarTreeDistance {
        val compareList = ArrayList<StarTreeDistance>(3)
        val parent = currentStarTree.parent
        //root has no neighbors
        if (parent != null && !currentStarTree.root) {
            val parentDistance = StarTreeDistance(parent as StarTree, currentStarTree.parent?.ifSpatial { position }?.worldPosition()?.distance(controller.worldPosition())!!)
            val nearestChild = nearestChild(parent)
            compareList.add(parentDistance)
            compareList.add(nearestChild)
        }
        compareList.add(StarTreeDistance(currentStarTree, currentStarTree.spatial().worldPosition().distance(controller.worldPosition())))
        compareList.add(nearestChild(currentStarTree))
        return compareList.sortedBy { it.distance }[0]
    }

    private fun nearestChild(parent: Node): StarTreeDistance {
        val nearestChild = parent.children.filter { it.name.contains("StarTree") }
            .map { it to it.ifSpatial { position }?.worldPosition()?.distance(controller.worldPosition()) }
            .sortedBy { it.second }[0]
        return StarTreeDistance(nearestChild.first as StarTree, nearestChild.second!!)
    }

    companion object {
        private data class StarTreeDistance(val starTree: StarTree, val distance: Float)
        /**
         * Convenience method for adding tool select behaviour
         */
        fun createAndSet(
            scene: Scene,
            hmd: OpenVRHMD,
            getHmdPos: () -> Vector3f,
            button: List<OpenVRHMD.OpenVRButton>,
            controllerSide: List<TrackerRole>,
            starTree: StarTree,
        ) {
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    device.model?.let { controller ->
                        if (controllerSide.contains(device.role)) {
                            val name = "VRDrag:${hmd.trackingSystemName}:${device.role}:$button"
                            val vrToolSelector = VRSelectionStarTree(
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial."),
                                scene,
                                getHmdPos,
                                starTree
                            )
                            hmd.addBehaviour(name, vrToolSelector)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                        }
                    }
                }
            }
        }
    }
}
