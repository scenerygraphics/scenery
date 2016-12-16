package graphics.scenery.scenery

import cleargl.GLVector

/**
 * Point light class.
 *
 * Point lights have no extent, but carry a [linear] and [quadratic] falloff.
 * They also have an optional [Box] to accompany them for easier visualisation.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @constructor Creates a PointLight with default settings, e.g. white emission color.
 */
class PointLight : Mesh(), HasGeometry, Renderable {
    /** The intensity of the point light. Bound to [0.0, 1.0] if using non-HDR rendering. */
    var intensity: Float = 0.5f
    /** The emission color of the point light. Setting it will also affect the accompanying Box' color. */
    var emissionColor: GLVector = GLVector(1.0f, 1.0f, 1.0f)
        set(value) {
            this.children[0].material?.diffuse = value
            field = value
        }

    /** Linear falloff of the light. */
    var linear: Float = 10.5f
    /** Quadratic falloff of the light. */
    var quadratic: Float = 2.7f

    /** Node name of the Point Light */
    override var name = "PointLight"

    init {
        super.init()

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))
        box.material = Material()
        box.material?.diffuse = this.emissionColor
        box.visible = false

        this.addChild(box)
    }

    /**
     * This method shows the accompanying Box of the Point Light for easier visualisation
     * and inspection.
     */
    fun showLightBox() {
        this.children[0].visible = true
    }

    /**
     * This method hides the accompanying Box of the Point Light.
     */
    fun hideLightBox() {
        this.children[0].visible = false
    }
}
