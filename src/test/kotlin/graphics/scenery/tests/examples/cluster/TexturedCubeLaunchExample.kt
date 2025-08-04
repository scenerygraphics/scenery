package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.tests.examples.cluster.DemoReelExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.utils.lazyLogger
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.io.path.absolute

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TexturedCubeLaunchExample {

    companion object {
        private val logger by lazyLogger()
        @JvmStatic
        fun main(args: Array<String>) {
            thread {
                val client = AutofabClient()
                Thread.sleep(5000)

                //Runtime.getRuntime().addShutdownHook(thread(start = false) {
                //    client.shutdownLaunchedProcesses()
                //})

//                val clusterLaunch = Runtime.getRuntime().exec("C:\\Users\\mosaic\\Code\\scenery-base\\run-cluster.bat graphics.scenery.tests.examples.cluster.CaveClientExample",
//                    null, Paths.get(".").absolute().parent.parent.toFile())

                while(client.listAvailableHosts().size == 0) {
                    Thread.sleep(1000)
                    logger.info("Waiting for available hosts...")
                }
                val hosts = client.listAvailableHosts()
                logger.info("Available hosts are ${hosts.joinToString { it.hostAddress }}")
                val clusterLaunchCommand = """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dorg.lwjgl.system.stackSize=300 -Dscenery.VulkanRenderer.UseOpenGLSwapchain=false -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=false -Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5001 -Dscenery.ScreenName=front graphics.scenery.tests.examples.basic.TexturedCubeExample"""

                client.launchOnAvailableHosts(clusterLaunchCommand, register = false)

                //Thread.sleep(10000)

                //client.shutdownLaunchedProcesses()

//                BufferedReader(InputStreamReader(clusterLaunch.inputStream)).use { input ->
//                    var line: String?
//                    while (input.readLine().also { line = it } != null) {
//                        DemoReelExample.logger.info("Cluster: $line")
//                    }
//                }
            }
        }
    }
}

