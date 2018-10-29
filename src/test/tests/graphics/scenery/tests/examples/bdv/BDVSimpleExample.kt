package graphics.scenery.tests.examples.bdv

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.Hololens
import graphics.scenery.volumes.bdv.BDVVolume
import org.junit.Test
import tpietzsch.scenery.example0.RenderStuff2
import tpietzsch.shadergen.DefaultShader
import tpietzsch.shadergen.generate.SegmentTemplate
import java.util.*

/**
 * Example that renders procedurally generated volumes on a [Hololens].
 * [bitsPerVoxel] can be set to 8 or 16, to generate Byte or UnsignedShort volumes.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class BDVSimpleExample: SceneryBase("BDV Rendering example", 1280, 720) {
    override fun init() {
        renderer = Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight)
        hub.add(SceneryElement.Renderer, renderer!!)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(-0.2f, 0.0f, 1.0f)
            perspectiveCamera(50.0f, 1.0f*windowWidth, 1.0f*windowHeight)
            active = true

            scene.addChild(this)
        }

        val volume = BDVVolume()
        volume.name = "volume"
        volume.colormap = "plasma"
        volume.scale = GLVector(0.02f, 0.02f, 0.02f)
        scene.addChild(volume)

        val lights = (0 until 3).map {
            PointLight(radius = 15.0f)
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i - 4.0f,  i - 1.0f, 0.0f)
            light.emissionColor = GLVector(1.0f, 1.0f, 1.0f)
            light.intensity = 50.0f
            scene.addChild(light)
        }

        val ex1vp = SegmentTemplate(RenderStuff2::class.java, "render.vp", Arrays.asList()).instantiate()
        val ex1fp = SegmentTemplate(RenderStuff2::class.java, "render2.fp", Arrays.asList()).instantiate()
        val prog = DefaultShader(ex1vp.code, ex1fp.code)
        prog.getUniform4f("color1").set(1.0f, 0.5f, 1.0f, 1.0f)
        prog.getUniform4f("color2").set(0.8f, 1.0f, 0.0f, 1.0f)
        prog.getUniform4f("color3").set(0.2f, 0.2f, 0.2f, 1.0f)

        prog.use(volume.context)
        prog.setUniforms(volume.context)
    }

    override fun inputSetup() {
        setupCameraModeSwitching()
    }

    @Test override fun main() {
        super.main()
    }
}
