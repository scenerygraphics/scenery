package graphics.scenery.atomicsimulations

import graphics.scenery.Hub
import graphics.scenery.Icosphere
import graphics.scenery.Mesh
import graphics.scenery.attribute.material.Material
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Vector3f

/**
 * This class represents an atomic simulation, i.e. a simulation that works on the atomic scale. It holds
 * objects that represent the atoms themselves, and, depending on data source, volumetric data (e.g. the electronic
 * density). An object of this class can be used directly to visualize such an atomic simulation, after reading
 * the appropriate data, as it is a child of the Mesh class.
 * [name] Name of this particular visualization.
 * [scalingFactor] Factor to scale positions of this simulation to allow for optimal visualization.
 * [atomicRadius] Radius of the atoms (in Bohr); this does not correspond to a physical radius, it's
 *               simply for visualization purposes.
 * [normalizeVolumetricDataTo] A value to normalize the volumetric data to. This is useful for dynamic
 *                             simulations, as elsewise each timestep will be normalized to itself.
 *                             If this value is >0, then all timesteps will be normalized to the same value
 *                             allowing for analysis of local changes.
 * [cubeStyle] Name of the software with which cube file was created (or comparable software). .cube is
 *             actually a very loosely defined standard. If we don't know anything about the cube file, we
 *             have no choice but to use Regex parsing, which impacts performance. If we know the source
 *             of the cube file, other assumptions can be made. Only relevant if cube files are used.
 * [rootFolder] Name of a folder, from which all files for this atomic simulation are read. If not
 *              empty, all file names provided to the object will be read from this directoy. This is helpful
 *              for larger series of simulations for which the source files are always read from the same
 *              directory.
 *
 * @author Lenz Fiedler <l.fiedler@hzdr.de>
 */
open class AtomicSimulation(name: String = "DFTSimulation", private val scalingFactor: Float,
                            private var atomicRadius: Float, private val normalizeVolumetricDataTo: Float=-1.0f,
                            private var cubeStyle: String = "unknown", private val rootFolder: String = "") : Mesh(name) {
    init {
        atomicRadius *= scalingFactor
    }
    /** Atoms of this simulation as spheres. */
    lateinit var atoms : Array<Icosphere>
        protected set
    /** Volumetric data, e.g. electronic density.. */
    lateinit var volumetricData : BufferedVolume
        protected set
    /** Simulation data, parsed from a DFT calculation output. */
    val simulationData: DFTParser = DFTParser(normalizeVolumetricDataTo)
    /** For dynamic cases: Current timepoint. */
    private var currentTimePoint : Int = 0

    /**
     * Creates an atomic simulation object from a cube file with filename [filename], assigned to
     * a hub instance [hub].
     * */
    fun createFromCube(filename: String, hub: Hub){
        if (this.rootFolder.isEmpty()) {
            simulationData.parseCube(filename, cubeStyle)
        } else {
            simulationData.parseCube(this.rootFolder + filename, cubeStyle)
        }

        // Visualize the atoms.
        atoms = Array<Icosphere>(simulationData.numberOfAtoms) { Icosphere(atomicRadius, 4) }
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
        // TODO: Ask @randomdefaultuser if the following line has any use
        // simulationData.unitCellDimensions = simulationData.unitCellDimensions

//         Visualize the density data.
        volumetricData = Volume.fromBuffer(emptyList(), simulationData.gridDimensions[0],
            simulationData.gridDimensions[1],
            simulationData.gridDimensions[2],
            UnsignedByteType(), hub)
        volumetricData.name = "volume"
        // Note: Volumes centered at the origin are currently offset by -2.0 in each direction
        // (see Volume.kt, line 338), so we're adding 2.0 here.
        volumetricData.spatial().position = (scalingFactor * Vector3f(simulationData.unitCellOrigin[0],
            simulationData.unitCellOrigin[1],
            simulationData.unitCellOrigin[2])).add(
            Vector3f(2.0f, 2.0f, 2.0f)
        )

        volumetricData.colormap = Colormap.get("viridis")
        volumetricData.pixelToWorldRatio = simulationData.gridSpacings[0] * scalingFactor

        volumetricData.transferFunction = TransferFunction.ramp(0.0f, 0.3f, 0.5f)
        this.addChild(volumetricData)
        volumetricData.addTimepoint("t-0", simulationData.electronicDensityUByte)
        volumetricData.goToLastTimepoint()
    }
    /** Updates an existing atomic simulation from a cube file (for dynamic simulations). */
    fun updateFromCube(filename: String){
        if (this.rootFolder.isEmpty()) {
            simulationData.parseCube(filename, cubeStyle)
        } else {
            simulationData.parseCube(this.rootFolder + filename, cubeStyle)
        }
        // Visualize the atoms.
        atoms.zip(simulationData.atomicPositions).forEach {
            it.component1().spatial().position = scalingFactor * it.component2()

        }
        currentTimePoint++
        volumetricData.addTimepoint("t-${currentTimePoint}", simulationData.electronicDensityUByte)
        volumetricData.goToLastTimepoint()
        volumetricData.purgeFirst(10, 10)
    }
    /** Updates the material of the atoms. */
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

    /**
     * Creates a DFT simulation object from a cube file containing some volumetric data and atomic positions.
     * [name] Name of this particular visualization.
     * [scalingFactor] Factor to scale positions of this simulation to allow for optimal visualization.
     * [atomicRadius] Radius of the atoms (in Bohr); this does not correspond to a physical radius, it's
     *                simply for visualization purposes.
     * [normalizeVolumetricDataTo] A value to normalize the volumetric data to. This is useful for dynamic
     *                             simulations, as elsewise each timestep will be normalized to itself.
     *                             If this value is >0, then all timesteps will be normalized to the same value
     *                             allowing for analysis of local changes.
     * [cubeStyle] Name of the software with which cube file was created (or comparable software). .cube is
     *             actually a very loosely defined standard. If we don't know anything about the cube file, we
     *             have no choice but to use Regex parsing, which impacts performance. If we know the source
     *             of the cube file, other assumptions can be made. Only relevant if cube files are used.
     * [rootFolder] Name of a folder, from which all files for this atomic simulation are read. If not
     *              empty, all file names provided to the object will be read from this directoy. This is helpful
     *              for larger series of simulations for which the source files are always read from the same
     *              directory.

     */
    companion object {
        @JvmStatic fun fromCube(filename: String, hub: Hub, scalingFactor: Float, atomicRadius: Float,
                                normalizeVolumetricDataTo:Float = -1.0f, cubeStyle: String = "unknown",
                                rootFolder: String = ""):
            AtomicSimulation {
            val dftSimulation = AtomicSimulation(scalingFactor=scalingFactor, atomicRadius=atomicRadius,
                normalizeVolumetricDataTo=normalizeVolumetricDataTo, cubeStyle=cubeStyle, rootFolder = rootFolder)
            dftSimulation.createFromCube(filename, hub)
            return dftSimulation
        }
    }

}
