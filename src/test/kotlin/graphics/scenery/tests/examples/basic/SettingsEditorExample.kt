package graphics.scenery.tests.examples.basic


import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import kotlin.concurrent.thread

/**
 * @author Konrad Michel <konrad.michel@mailbox.tu-dresden.de>
 *
 * Example to show the use of a custom settings object, loaded into the SettingsEditor as a graphical visualization.
 * Also shows how to use settings updateRoutines.
 */
class SettingsEditorExample : SceneryBase("SettingsEditorExample", 1024, 1024, false) {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"
        box.material {
            textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
            metallic = 0.3f
            roughness = 0.9f
        }
        scene.addChild(box)

        val light = PointLight(radius = 15.0f)
        light.spatial().position = Vector3f(0.0f, 0.0f, 2.0f)
        light.intensity = 5.0f
        light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
        scene.addChild(light)

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)

            scene.addChild(this)
        }

        val cubeSettings = Settings(hub)
        cubeSettings.setIfUnset("RotationSpeed", 5.0f)
        cubeSettings.setIfUnset("RotationSpeedChange", 0.05f)
        cubeSettings.addUpdateRoutine("RotationSpeedChange",
            {
                logger.info("Changed 'RotationSpeed' to ${cubeSettings.getProperty<String>("RotationSpeed")}")
                logger.info("Changed 'RotationSpeedChange' is ${cubeSettings.getProperty<String>("RotationSpeedChange")}")
            } )

        val settingsEditor = SettingsEditor(cubeSettings)


        thread {
            while (running) {
                box.spatial {
                    rotation.rotateY(0.01f * cubeSettings.getProperty<Float>("RotationSpeed"))
                    needsUpdate = true
                }

                Thread.sleep(20)
            }
        }

        thread {
            while (true) {
                Thread.sleep(200)
                cubeSettings.set("RotationSpeed", cubeSettings.getProperty<Float>("RotationSpeed") + cubeSettings.getProperty<Float>("RotationSpeedChange"))
            }
        }
    }

    /**
     * Static object for running as application
     */
    companion object {
        /**
         * Main method for the application, that instances and runs the example.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            SettingsEditorExample().main()
        }
    }
}

