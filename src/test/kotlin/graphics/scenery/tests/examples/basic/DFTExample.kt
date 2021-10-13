package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.atomicsimulations.AtomicSimulation
import graphics.scenery.backends.Renderer

/**
 * <Description>
 *  Visualizes a DFT snapshot with volumetric data (electronic density).
 *
 * @author Lenz Fiedler <l.fiedler@hzdr.de>
 */
class DFTExample : SceneryBase("DFTExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))

        // Create an object for the DFT simulation.
        val atomicSimulation = AtomicSimulation.fromCube(getDemoFilesPath() + "/dft_data/Fe_snapshot0_dens.cube",
            hub,0.5f, 0.5f)
        scene.addChild(atomicSimulation)

        // One light in every corner.

        val lights = (0 until 8).map {
            PointLight(radius = 15.0f)
        }
        lights.mapIndexed { i, light ->
            val permutation = String.format("%3s", Integer.toBinaryString(i)).replace(' ', '0')
            light.spatial().position = Vector3f(atomicSimulation.simulationData.unitCellDimensions.x * (permutation[0].code-48) ,
                atomicSimulation.simulationData.unitCellDimensions.y * (permutation[1].code-48),
                atomicSimulation.simulationData.unitCellDimensions.z * (permutation[2].code-48))
            light.emissionColor = Vector3f(1.0f, 1.0f, 1.0f)
            light.intensity = 1.0f
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
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            DFTExample().main()
        }
    }
}

