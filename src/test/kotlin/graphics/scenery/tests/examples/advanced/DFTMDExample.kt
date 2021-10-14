package graphics.scenery.tests.examples.advanced

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.atomicsimulations.AtomicSimulation
import graphics.scenery.backends.Renderer
import kotlin.concurrent.thread

/**
 *  Visualizes a DFT-MD trajectory with volumetric data (electronic density).
 *
 * @author Lenz Fiedler <l.fiedler@hzdr.de>
 */
class DFTMDExample : SceneryBase("DFTMDExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        // Create an object for the DFT (-MD) simulation.
        val atomicSimulation = AtomicSimulation.fromCube("Fe_snapshot0_dens.cube",
            hub,0.5f, 0.5f, rootFolder = getDemoFilesPath() + "/dft_data/")
        scene.addChild(atomicSimulation)

        // One light in every corner.
        val lights = (0 until 8).map {
            PointLight(radius = 15.0f)
        }
        lights.mapIndexed { i, light ->
            val permutation = String.format("%3s", Integer.toBinaryString(i)).replace(' ', '0')
            light.spatial().position = Vector3f(atomicSimulation.simulationData.unitCellDimensions[0] *
                (permutation[0].code-48),
                atomicSimulation.simulationData.unitCellDimensions[1] * (permutation[1].code-48),
                atomicSimulation.simulationData.unitCellDimensions[2] * (permutation[2].code-48))
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 5.0f)
            }
            perspectiveCamera(50.0f, 512, 512)
            scene.addChild(this)
        }

        var currentSnapshot = 0
        val maxSnapshot = 10
        var count = 0
        thread {
            while (running) {
                atomicSimulation.updateFromCube("Fe_snapshot${currentSnapshot}_dens.cube")
                Thread.sleep(500)
                currentSnapshot = (currentSnapshot + 1) % maxSnapshot
                count++
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            DFTMDExample().main()
        }
    }
}

