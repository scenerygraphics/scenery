package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.utils.lazyLogger
import kotlin.concurrent.thread

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class TestingClusterLaunchExample {

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
                // -Dscenery.VulkanRenderer.UseOpenGLSwapchain=false
                val clusterLaunchCommand = """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dorg.lwjgl.system.stackSize=300 -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=false -Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.ServerAddress=tcp://10.1.2.201 -Dscenery.RemoteCamera=true --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED graphics.scenery.tests.examples.cluster.SlimClient"""

                client.shutdownLaunchedProcesses()

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

