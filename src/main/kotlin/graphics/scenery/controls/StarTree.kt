package graphics.scenery.controls

import graphics.scenery.Mesh
import graphics.scenery.Sphere
import graphics.scenery.primitives.TextBoard
import org.joml.Vector3f
import org.joml.Vector4f

class StarTree(textBoardText: String = "", val starTreeChildren: List<StarTree> = listOf(), val root: Boolean = false,
               val action: () -> Unit = {}, ): Mesh("StarTree") {
    init {
        if(!root) {
            starTreeChildren.forEach { it.visible = false }
            val sphere = Sphere(0.025f, 10)
            sphere.name = "StarSphere"
            this.addChild(sphere)
            val board = TextBoard()
            board.text = textBoardText
            board.name = "ToolSelectTextBoard"
            board.transparent = 0
            board.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
            board.backgroundColor = Vector4f(100f, 100f, 100f, 1.0f)
            board.spatial {
                position = Vector3f(0f, 0.05f, 0f)
                scale = Vector3f(0.05f, 0.05f, 0.05f)
            }
            this.addChild(board)
        }
        // child positions
        starTreeChildren.forEach {
            this.addChild(it)
            it.parent = this
        }
    }

    fun showChildren() {
        this.children.filterIsInstance<StarTree>().forEach { it.visible = true }
    }

    fun hideChildren() {
        this.children.filterIsInstance<StarTree>().forEach {
            //first level is always visible
            if(!root) { it.visible = false }
            if (it is StarTree) {
                it.hideChildren()
            }
        }
    }

    fun arrangeChildren(previousRotation: Float = 0f) {
        this.children.filterIsInstance<StarTree>().forEachIndexed { index, child ->
            val pos = Vector3f(0f, .15f, 0f)
            val rotationAngle = if(root) { (2f * Math.PI.toFloat() / starTreeChildren.size) * index }
                                else { ((7f/8f*Math.PI.toFloat() / starTreeChildren.size) * index - 7f/16f*Math.PI.toFloat() + previousRotation)%(2f*Math.PI).toFloat() }
            pos.rotateZ(rotationAngle)
            child.ifSpatial { position = pos }
            child.arrangeChildren(rotationAngle)
        }
    }

}
