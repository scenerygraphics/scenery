package graphics.scenery.controls

import graphics.scenery.Mesh
import graphics.scenery.Sphere
import graphics.scenery.primitives.TextBoard
import org.joml.Vector3f
import org.joml.Vector4f

class StarTree(textBoardText: String = "", val starTreeChildren: List<StarTree> = listOf(), val action: () -> Unit = {},): Mesh("StarTree") {
    var root = false
    var parentStarTree: StarTree? = null
    init {
        starTreeChildren.forEach { it.parentStarTree = this }
        if (parentStarTree != null) {
            val sphere = Sphere(0.025f, 10)
            sphere.name = "StarTreeSphere"
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
        else {
            root = true
        }
        // child positions
        starTreeChildren.forEachIndexed { index, it ->
            val pos = Vector3f(0f, .15f, 0f)
            pos.rotateZ((2f * Math.PI.toFloat() / starTreeChildren.size) * index)
            this.addChild(it)
            it.spatial().position = pos
        }
    }

    fun inkrementRoot() {
        this.spatial().scale.mul(1f / 1.61f)
        this.ifMaterial {
            roughness = 1 / 1.61f
        }
    }

    fun setRoot() {
        this.visible = true
        if (!this.children.isEmpty()) {
            this.children.filter { it.name == "ToolSelectTextBoard" }[0].visible = false
        }
    }

    fun decrementRoot() {
        this.visible = false
        this.starTreeChildren.forEach {
            it.decrementRoot()
        }
        this.parentStarTree?.spatial()?.scale?.mul(1.61f)
        this.parentStarTree?.ifMaterial {
            roughness = 1f
        }
    }
}
