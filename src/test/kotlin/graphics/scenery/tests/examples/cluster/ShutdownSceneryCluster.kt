package graphics.scenery.tests.examples.cluster

import graphics.scenery.*
import graphics.scenery.utils.lazyLogger
import kotlin.system.exitProcess

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ShutdownSceneryCluster {

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

