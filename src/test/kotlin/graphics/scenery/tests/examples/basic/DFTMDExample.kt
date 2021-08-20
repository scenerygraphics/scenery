package graphics.scenery.tests.examples.basic

import org.joml.Vector3f
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import kotlin.concurrent.thread

/**
 * <Description>
 *  Visualizes a DFT snapshot.
 *
 * @author Lenz Fiedler <l.fiedler@hzdr.de>
 */
class DFTMDExample : SceneryBase("DFTExample") {
    override fun init() {
        renderer = hub.add(SceneryElement.Renderer,
            Renderer.createRenderer(hub, applicationName, scene, 512, 512))
        val snapshot = DFTParser()
        snapshot.parseCube(getDemoFilesPath() + "/volumes/dft/Fe_snapshot0_dens.cube")

        // Scales the DFT coordinates (which are in Bohr units) for a better VR experience.
        val scalingFactor = 0.5f

        // Visualize the atoms.
        val atomicRadius = 0.5f*scalingFactor
        val atoms: Array<Icosphere> = Array<Icosphere>(snapshot.numberOfAtoms) {Icosphere(atomicRadius, 4)}
        atoms.zip(snapshot.atomicPositions).forEach {
            with(it.component1()){
                spatial{
                    position = it.component2().mul(scalingFactor)
                }
                material{
                    metallic = 0.3f
                    roughness = 0.9f
                }
            }
            scene.addChild(it.component1())
        }

        // Visualize the density data.
        val volume = Volume.fromBuffer(emptyList(), snapshot.gridDimensions[0], snapshot.gridDimensions[1],
            snapshot.gridDimensions[2], UnsignedByteType(), hub)

        volume.name = "volume"
        // Note: Volumes centered at the origin are currently offset by -2.0 in each direction
        // (see Volume.kt, line 338), so we're adding 2.0 here.
        volume.spatial().position = (Vector3f(snapshot.unitCellOrigin[0],snapshot.unitCellOrigin[1],
            snapshot.unitCellOrigin[2]).mul(scalingFactor)).add(
            Vector3f(2.0f, 2.0f, 2.0f))
        volume.colormap = Colormap.get("viridis")
        volume.pixelToWorldRatio = snapshot.gridSpacings[0]*scalingFactor
        volume.transferFunction = TransferFunction.ramp(0.0f, 0.3f, 0.5f)
        scene.addChild(volume)


        // One light in every corner.
        val lights = (0 until 8).map {
            PointLight(radius = 15.0f)
        }
        lights.mapIndexed { i, light ->
            val permutation = String.format("%3s", Integer.toBinaryString(i)).replace(' ', '0')
            light.spatial().position = Vector3f(snapshot.unitCellDimensions[0] * (permutation[0].code-48) ,
                snapshot.unitCellDimensions[1] * (permutation[1].code-48),
                snapshot.unitCellDimensions[2] * (permutation[2].code-48))
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

        var currentSnapshot = 0
        val maxSnapshot = 10
        var count = 0
        thread {
            while (running) {
                // Read new MD snapshot.
                snapshot.parseCube(getDemoFilesPath() + "/volumes/dft/Fe_snapshot${currentSnapshot}_dens.cube")

                // Visualize the atoms.
                atoms.zip(snapshot.atomicPositions).forEach {
                    it.component1().spatial().position = it.component2().mul(scalingFactor)

                }
//                for(i in 0 until snapshot.numberOfAtoms) {
//                    atoms[i].spatial().position = (snapshot.atomicPositions[i]).mul(scalingFactor)
//                }

                volume.addTimepoint("t-${count}", snapshot.electronicDensityUByte)
                volume.goToLastTimepoint()
                volume.purgeFirst(10, 10)
                Thread.sleep(500)
                currentSnapshot = (currentSnapshot +1) % maxSnapshot
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

