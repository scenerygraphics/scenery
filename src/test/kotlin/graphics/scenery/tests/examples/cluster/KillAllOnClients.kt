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
import kotlin.system.exitProcess

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class KillAllOnClients {

    companion object {
        private val logger by lazyLogger()
        @JvmStatic
        fun main(args: Array<String>) {
            val client = AutofabClient()
            Thread.sleep(1000)
            while(client.listAvailableHosts().size == 0) {
                Thread.sleep(1000)
                println("Waiting for available hosts...")
            }
            val hosts = client.listAvailableHosts()
            println("Available hosts are ${hosts.joinToString { it.hostAddress }}")
            client.shutdownLaunchedProcesses()

            Thread.sleep(500)
            exitProcess(0)
        }
    }
}

