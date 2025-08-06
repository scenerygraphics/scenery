package graphics.scenery.tests.examples.cluster

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.InputHandler
import graphics.scenery.controls.TrackedStereoGlasses
import graphics.scenery.net.NodePublisher
import graphics.scenery.net.NodeSubscriber
import graphics.scenery.Mesh
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.scijava.ui.behaviour.ClickBehaviour
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.io.path.absolute
import kotlin.system.exitProcess

/**
 * Demo reel example to be run on a CAVE system.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class DemoReelLaunchExample: SceneryBase("Demo Reel") {

    companion object {
        private val logger by lazyLogger()
        var client: AutofabClient? = null
        @JvmStatic
        fun main(args: Array<String>) {
            val clusterScene = DemoReelExample()
//            val clusterScene = CaveBaseScene()

            client = AutofabClient()
            while(client!!.listAvailableHosts().size == 0) {
                Thread.sleep(1000)
                logger.info("Waiting for available hosts...")
            }

            val hosts = client!!.listAvailableHosts()
            logger.info("Available hosts are ${hosts.joinToString { it.hostAddress }}")

            var clusterLaunchCommand = """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dorg.lwjgl.system.stackSize=300 -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5000 -Dscenery.DriveLetter=E -Dscenery.RunFullscreen=true -Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.VulkanRenderer.UseOpenGLSwapchain=true -Dscenery.Renderer.Framelock=true -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.ServerAddress=tcp://10.1.2.201"""

            clusterLaunchCommand += """ --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.util.calendar=ALL-UNNAMED """
            clusterLaunchCommand +=  clusterScene::class.java.name

            //client!!.shutdownLaunchedProcesses()
            client!!.launchOnAvailableHosts(clusterLaunchCommand, register = false)

            clusterScene.main()
        }
    }
}
