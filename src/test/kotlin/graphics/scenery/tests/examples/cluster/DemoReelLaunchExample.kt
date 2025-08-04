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
            //if(System.getProperty("scenery.master") != null) {
                thread {
//                val clusterLaunch = Runtime.getRuntime().exec("C:\\Users\\mosaic\\Code\\scenery-base\\run-cluster.bat graphics.scenery.tests.examples.cluster.CaveClientExample",
//                    null, Paths.get(".").absolute().parent.parent.toFile())
                    //val client = AutofabClient()
                    //val hosts = client.listAvailableHosts()
                    //logger.info("Available hosts are ${hosts.joinToString { it.hostAddress }}")
//                val clusterLaunchCommand = """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dscenery.VulkanRenderer.UseOpenGLSwapchain=false -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=false-Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5001 -Dscenery.ScreenName=front graphics.scenery.tests.examples.basic.TexturedCubeExample"""
                    //val clusterLaunchCommand =
                    //    """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -Dorg.lwjgl.system.stackSize=500 -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dscenery.VulkanRenderer.UseOpenGLSwapchain=false -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=false-Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5001 -Dscenery.ScreenName=front graphics.scenery.tests.examples.basic.TexturedCubeExample"""

                    //client.launchOnAvailableHosts(clusterLaunchCommand, register = false)

//                BufferedReader(InputStreamReader(clusterLaunch.inputStream)).use { input ->
//                    var line: String?
//                    while (input.readLine().also { line = it } != null) {
//                        DemoReelExample.logger.info("Cluster: $line")
//                    }
//                }
                    client = AutofabClient()
                    while(client!!.listAvailableHosts().size == 0) {
                        Thread.sleep(1000)
                        logger.info("Waiting for available hosts...")
                    }

                    val hosts = client!!.listAvailableHosts()
                    logger.info("Available hosts are ${hosts.joinToString { it.hostAddress }}")
                    client!!.shutdownLaunchedProcesses()
                    val clusterLaunchCommand = """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dorg.lwjgl.system.stackSize=300 -Dscenery.VulkanRenderer.UseOpenGLSwapchain=true -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=true -Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5000 -Dscenery.DriveLetter=D """ + DemoReelExample::class.java.name//.substringBefore("$")

                    client!!.launchOnAvailableHosts(clusterLaunchCommand, register = false)
                }
            //}
            DemoReelExample().main()
        }
    }
}
