package scenery.tests.examples

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.GLAutoDrawable
import org.junit.Test
import scenery.*
import scenery.rendermodules.opengl.DeferredLightingRenderer

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class LineExample : SceneryDefaultApplication("LineExample") {
    override fun init(pDrawable: GLAutoDrawable) {
        super.init(pDrawable)
        deferredRenderer = DeferredLightingRenderer(pDrawable.gl.gL4, glWindow!!.width, glWindow!!.height)
        hub.add(SceneryElement.RENDERER, deferredRenderer!!)

        var linematerial = Material()
        linematerial.ambient = GLVector(1.0f, 0.0f, 0.0f)
        linematerial.diffuse = GLVector(0.0f, 1.0f, 0.0f)
        linematerial.specular = GLVector(1.0f, 1.0f, 1.0f)

        var line = Line()
        line.addPoint(GLVector(-1.0f, -1.0f, -1.0f))
        line.addPoint(GLVector(0.0f, 1.0f, 0.0f))
        line.addPoint(GLVector(2.0f, 0.0f, 2.0f))
        line.addPoint(GLVector(10.0f, 5.0f, 10.0f))
        line.material = linematerial
        line.preDraw()
        line.position = GLVector(0.0f, 0.0f, 1.1f)

        scene.addChild(line)

        var lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 0.8f * (i + 1);
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        cam.position = GLVector(0.0f, 0.0f, -5.0f)
        cam.view = GLMatrix().setCamera(cam.position, cam.position + cam.forward, cam.up)
        cam.projection = GLMatrix()
                .setPerspectiveProjectionMatrix(
                        70.0f / 180.0f * Math.PI.toFloat(),
                        pDrawable.surfaceWidth.toFloat() / pDrawable.surfaceHeight.toFloat(), 0.1f, 1000.0f)
                .invert()
        cam.active = true

        scene.addChild(cam)

        deferredRenderer?.initializeScene(scene)
    }


    @Test override fun main() {
        super.main()
    }
}
