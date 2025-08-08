package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.lazyLogger
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeExample : SceneryBase("TexturedCubeExample") {
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

        thread {
            while (running) {
                box.spatial {
                    rotation.rotateY(0.01f)
                    needsUpdate = true
                }

                Thread.sleep(20)
            }
        }
    }

    companion object {
        private val logger by lazyLogger()
        @JvmStatic
        fun main(args: Array<String>) {
//            thread {
//                Runtime.getRuntime().addShutdownHook(thread(start = false) {
//                    Runtime.getRuntime().exec("C:\\Users\\mosaic\\Code\\scenery-base\\killall-java.bat",
//                        null, Paths.get(".").absolute().parent.parent.toFile())
//                })
//
//                Thread.sleep(5000)
////                val clusterLaunch = Runtime.getRuntime().exec("C:\\Users\\mosaic\\Code\\scenery-base\\run-cluster.bat graphics.scenery.tests.examples.cluster.CaveClientExample",
////                    null, Paths.get(".").absolute().parent.parent.toFile())
//                val client = AutofabClient()
//                while(client.listAvailableHosts().size == 0) {
//                    Thread.sleep(1000)
//                    logger.info("Waiting for available hosts...")
//                }
//                val hosts = client.listAvailableHosts()
//                logger.info("Available hosts are ${hosts.joinToString { it.hostAddress }}")
//                val clusterLaunchCommand = """S:\\jdk\\temurin-21.0.3.9\\bin\\javaw.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dscenery.VulkanRenderer.UseOpenGLSwapchain=false -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=false-Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5001 -Dscenery.ScreenName=front graphics.scenery.tests.examples.basic.TexturedCubeExample"""
//
//                client.launchOnAvailableHosts(clusterLaunchCommand, register = true)
//
////                BufferedReader(InputStreamReader(clusterLaunch.inputStream)).use { input ->
////                    var line: String?
////                    while (input.readLine().also { line = it } != null) {
////                        DemoReelExample.logger.info("Cluster: $line")
////                    }
////                }
//            }
            TexturedCubeExample().main()
        }
    }
}

