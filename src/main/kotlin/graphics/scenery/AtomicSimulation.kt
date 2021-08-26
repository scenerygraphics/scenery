package graphics.scenery

import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Vector3f

open class AtomicSimulation(override var name: String = "DFTSimulation", private val scalingFactor: Float,
                            private var atomicRadius: Float, private val normalizeVolumetricDataTo: Float=-1.0f) : Mesh(name) {
    init {
        atomicRadius *= scalingFactor
    }

    lateinit var atoms : Array<Icosphere>
        protected set
    lateinit var volumetricData : BufferedVolume
        protected set
    val simulationData: DFTParser = DFTParser(normalizeVolumetricDataTo)
    private var currentTimePoint : Int = 0

    fun createFromCube(filename: String, hub: Hub){
        simulationData.parseCube(filename)

        // Visualize the atoms.
        atoms = Array<Icosphere>(simulationData.numberOfAtoms) {Icosphere(atomicRadius, 4)}
        atoms.zip(simulationData.atomicPositions).forEach {
            with(it.component1()){
                spatial {
                    position = scalingFactor * it.component2()
                }
                material {
                    metallic = 0.3f
                    roughness = 0.9f
                }
            }
            this.addChild(it.component1())
        }
        simulationData.unitCellDimensions = simulationData.unitCellDimensions

//         Visualize the density data.
        volumetricData = Volume.fromBuffer(emptyList(), simulationData.gridDimensions[0], simulationData.gridDimensions[1],
            simulationData.gridDimensions[2], UnsignedByteType(), hub)
        volumetricData.name = "volume"
        // Note: Volumes centered at the origin are currently offset by -2.0 in each direction
        // (see Volume.kt, line 338), so we're adding 2.0 here.
        volumetricData.spatial().position = (scalingFactor* Vector3f(simulationData.unitCellOrigin[0],simulationData.unitCellOrigin[1],
            simulationData.unitCellOrigin[2])).add(
            Vector3f(2.0f, 2.0f, 2.0f)
        )

        volumetricData.colormap = Colormap.get("viridis")
        volumetricData.pixelToWorldRatio = simulationData.gridSpacings[0]*scalingFactor

        volumetricData.transferFunction = TransferFunction.ramp(0.0f, 0.3f, 0.5f)
        this.addChild(volumetricData)
        volumetricData.addTimepoint("t-0", simulationData.electronicDensityUByte)
        volumetricData.goToLastTimepoint()
    }

    fun updateFromCube(filename: String){
        simulationData.parseCube(filename)
        // Visualize the atoms.
        atoms.zip(simulationData.atomicPositions).forEach {
            it.component1().spatial().position = scalingFactor * it.component2()

        }
        currentTimePoint++
        volumetricData.addTimepoint("t-${currentTimePoint}", simulationData.electronicDensityUByte)
        volumetricData.goToLastTimepoint()
        volumetricData.purgeFirst(10, 10)
    }

    fun updateAtomicMaterial(newMaterial: Material){
        this.atoms.forEach { atom ->
            with(atom)
            {
                material {
                    roughness = newMaterial.roughness
                    metallic = newMaterial.metallic
                    diffuse = newMaterial.diffuse
                }
            }
        }
    }


    companion object {
        /**
         * Creates a DFT simulation object from a cube file containing some volumetric data and atomic positions.
         */
        @JvmStatic fun fromCube(filename: String, hub: Hub, scalingFactor: Float, atomicRadius: Float,
                                normalizeVolumetricDataTo:Float = -1.0f): AtomicSimulation {
            val dftSimulation = AtomicSimulation(scalingFactor=scalingFactor, atomicRadius=atomicRadius,
                normalizeVolumetricDataTo=normalizeVolumetricDataTo)
            dftSimulation.createFromCube(filename, hub)
            return dftSimulation
        }
    }

}
