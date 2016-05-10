package scenery

import cleargl.GLVector

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class PointLight : Mesh(), HasGeometry, Renderable {
    var intensity: Float = 0.5f
    var emissionColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)
        set(value) {
            this.children[0].material?.diffuse = value
            field = value
        }

    var linear: Float = 10.5f
    var quadratic: Float = 2.7f

    override var name = "PointLight"

    init {
        super.init()

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))
        box.material = Material()
        box.material?.diffuse = this.emissionColor
        box.visible = false

        this.addChild(box)
    }

    fun showLightBox() {
        this.children[0].visible = true
    }

    fun hideLightBox() {
        this.children[0].visible = false
    }

    override fun preDraw() {
    }
}