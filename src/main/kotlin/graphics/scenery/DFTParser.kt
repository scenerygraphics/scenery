package graphics.scenery
import graphics.scenery.repl.REPL
import graphics.scenery.utils.RingBuffer
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

/**
Parses a density functional theory (common simulation method in solid state physics and theoretical chemistry)
calculation. Can be used to visualize single DFT calculations or DFT-MD (=DFT molecular dynamics simulations).

 * @property[normalizeDensityTo] Defines to which value the density is scaled. This is useful when visualizing more then one DFT calculation at the
    same time, in order to keep the density visualization consistent. Negative values mean the density is scaled
    per snapshot. Default is -1.0f.
 */
class DFTParser (val normalizeDensityTo: Float = -1.0f){
    /**  Number of Atoms.*/
    var numberOfAtoms:Int = 0
    /** Distance between two grid points in either direction in Bohr.*/
    var gridSpacings = FloatArray(3) { 0.0f }
    /** Number of gridpoints in 3D grid.*/
    var gridDimensions = IntArray(3) { 0 }
    /** Positions of the atoms in Bohr.*/
    var atomicPositions = Array(0){ Vector3f()}
    private var electronicDensity = Array(0, {Array(0, { FloatArray(0) } ) } )
    /** Electronic density as scaled bytes, in scaled(1/Bohr).*/
    lateinit var electronicDensityUInt : ByteBuffer
    /** Origin of the unit cell, usually 0,0,0. */
    var unitCellOrigin = FloatArray(3) { 0.0f }
    /** Dimensions of the unit cell, in Bohr.*/
    var unitCellDimensions = FloatArray(3) { 0.0f }

    // Parse information as .cube file.
    fun parseCube(filename: String){
        // Read entire file content
        val cubeFile =  File(filename).bufferedReader().readLines()
        var counter = 0
        var xcounter = 0
        var ycounter = 0
        var zcounter = 0
        var minDensity = 1000.0f
        var maxDensity = 0.0f

        // Iterate through file. We know what is were with a cube file.
        // 0-1: Comments.
        // 2: Number of atoms + origin.
        // 3,4,5: Grid spacing in x,y,z direction (in Bohr)
        // 6 - (number_of_atoms+6): One line for each atom with position and species
        // Everything thereafter: the volumetric data, here the density.

        for (line in cubeFile){
            when (counter){
                0,1 ->{}
                2 -> {
                    numberOfAtoms = (line.trim().split("\\s+".toRegex())[0]).toInt()
                    unitCellOrigin[0] = (line.trim().split("\\s+".toRegex())[1]).toFloat()
                    unitCellOrigin[1] = (line.trim().split("\\s+".toRegex())[1]).toFloat()
                    unitCellOrigin[2] = (line.trim().split("\\s+".toRegex())[1]).toFloat()

                    // Now we know how many atoms we have.
                    atomicPositions = Array(numberOfAtoms){ Vector3f()}
                }
                3,4,5 -> {
                    gridDimensions[counter-3] =  (line.trim().split("\\s+".toRegex())[0]).toInt()
                    gridSpacings[counter-3] = (line.trim().split("\\s+".toRegex())[counter-2]).toFloat()
                }
                else->  {
                    if (counter == 6)
                    {
                        unitCellDimensions[0] = (gridSpacings[0]*gridDimensions[0])+unitCellOrigin[0]
                        unitCellDimensions[1] = (gridSpacings[1]*gridDimensions[1])+unitCellOrigin[1]
                        unitCellDimensions[2] = (gridSpacings[2]*gridDimensions[2])+unitCellOrigin[2]
                        electronicDensity = Array(gridDimensions[0], { Array(gridDimensions[1],
                            { FloatArray(gridDimensions[2]) } ) } )
                    }
                    // Parsing atomic positions.
                    if (counter < 6+numberOfAtoms){
                        atomicPositions[counter-6] = Vector3f((line.trim().split("\\s+".toRegex())[2]).toFloat(),
                                                              (line.trim().split("\\s+".toRegex())[3]).toFloat(),
                                                              (line.trim().split("\\s+".toRegex())[4]).toFloat())
                    }

                    // Parsing volumetric data.
                    // A possible optimization here would be to read this into a 1D array. We cannot directly
                    // read it into the byte buffer, because we don't know max/min values a-priori.
                    if (counter >= 6+numberOfAtoms) {
                        for (value in (line.trim().split("\\s+".toRegex()))) {
                            // Cube files should be in Fortran (z-fastest ordering).
                            electronicDensity[xcounter][ycounter][zcounter] = (value).toFloat()
                            if (electronicDensity[xcounter][ycounter][zcounter] > maxDensity) {
                                maxDensity = electronicDensity[xcounter][ycounter][zcounter]
                            }
                            if (electronicDensity[xcounter][ycounter][zcounter] < minDensity) {
                                minDensity = electronicDensity[xcounter][ycounter][zcounter]
                            }

                            zcounter++
                            if (zcounter == gridDimensions[2]) {
                                zcounter = 0
                                ycounter++
                                if (ycounter == gridDimensions[1]) {
                                    ycounter = 0
                                    xcounter++
                                }
                            }
                        }
                    }
                }
            }
            counter++
        }
        // Converting to byte buffer.
        counter = 0
        electronicDensityUInt =  MemoryUtil.memAlloc((gridDimensions[0]*
            gridDimensions[1]*gridDimensions[2]*1))
        if (normalizeDensityTo > 0.0f){
            maxDensity = normalizeDensityTo
        }
        for (z in 0 until gridDimensions[2]){
            for (y in 0 until gridDimensions[1]){
                for (x in 0 until gridDimensions[0]){
                    val value = (((electronicDensity[x][y][z] - minDensity) / (maxDensity - minDensity)) * 255.0f).toInt()
                    electronicDensityUInt.put(value.toByte())
                    counter++
                }
            }
        }
        electronicDensityUInt.flip()

    }
}
