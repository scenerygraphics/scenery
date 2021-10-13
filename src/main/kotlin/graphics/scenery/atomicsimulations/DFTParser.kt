package graphics.scenery.atomicsimulations
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.nio.ByteBuffer

/**
 * This class parses density functional theory (common simulation method in solid state physics and theoretical
 * chemistry) calculation. Can be used to visualize single DFT calculation or DFT-MD (=DFT molecular dynamics).
 * @property[normalizeDensityTo] Defines to which value the density is scaled. This is useful when visualizing more then one DFT calculation at the
 * same time, in order to keep the density visualization consistent. Negative values mean the density is scaled
 * per snapshot. Default is -1.0f.
 *
 * @author Lenz Fiedler <l.fiedler@hzdr.de>
 */
class DFTParser (private val normalizeDensityTo: Float = -1.0f): AutoCloseable{
    /**  Number of Atoms.*/
    var numberOfAtoms: Int = 0

    /** Distance between two grid points in either direction in Bohr.*/
    var gridSpacings = Vector3f( 0.0f, 0.0f, 0.0f)

    /** Number of gridpoints in 3D grid.*/
    var gridDimensions = Vector3i( 0, 0, 0)

    /** Positions of the atoms in Bohr.*/
    var atomicPositions = Array(0){ Vector3f()}

    /** Electronic density as float values.*/
    private var electronicDensity = FloatArray(0)

    /** Electronic density as scaled bytes, in scaled(1/Bohr).*/
    lateinit var electronicDensityUByte: ByteBuffer
        protected set

    /** Indicates whether memory was allocated for electronic density */
    private var electronicDensityMemory: Int = -1

    /** Origin of the unit cell, usually 0,0,0. */
    var unitCellOrigin = Vector3f( 0.0f, 0.0f, 0.0f)

    /** Dimensions of the unit cell, in Bohr.*/
    var unitCellDimensions = Vector3f( 0.0f, 0.0f, 0.0f)

    /** Closes this buffer, freeing all allocated resources on host and device. */
    override fun close() {
        // Only free memory if we allocated some during parsing.
        if (electronicDensityMemory > 0){
            MemoryUtil.memFree(electronicDensityUByte)
        }
    }

    /**
     * Parse information as .cube file.
     * [filename] Name of the file that is parsed.
     * [cubeStyle] Name of the software with which cube file was created (or comparable software). .cube is
     *             actually a very loosely defined standard. If we don't know anything about the cube file, we
     *             have no choice but to use Regex parsing, which impacts performance. If we know the source
     *             of the cube file, other assumptions can be made.
     */
    fun parseCube(filename: String, cubeStyle: String="unknown"){
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
        val whiteSpaceRegex = Regex("\\s+")
        for (line in cubeFile){
            when (counter){
                0,1 ->{}
                2 -> {
                    val lineContent = (line.trim().split(whiteSpaceRegex))
                    numberOfAtoms = lineContent[0].toInt()
                    unitCellOrigin.x = lineContent[1].toFloat()
                    unitCellOrigin.y = lineContent[2].toFloat()
                    unitCellOrigin.z = lineContent[3].toFloat()

                    // Now we know how many atoms we have.
                    atomicPositions = Array(numberOfAtoms){ Vector3f()}
                }
                3 -> {
                    val lineContent = (line.trim().split(whiteSpaceRegex))
                    gridDimensions.x =  lineContent[0].toInt()
                    gridSpacings.x = lineContent[1].toFloat()
                }
                4 -> {
                    val lineContent = (line.trim().split(whiteSpaceRegex))
                    gridDimensions.y =  lineContent[0].toInt()
                    gridSpacings.y = lineContent[2].toFloat()
                }
                5 -> {
                    val lineContent = (line.trim().split(whiteSpaceRegex))
                    gridDimensions.z =  lineContent[0].toInt()
                    gridSpacings.z = lineContent[3].toFloat()
                }
                else->  {
                    if (counter == 6)
                    {
                        unitCellDimensions.x = (gridSpacings[0]*gridDimensions[0])+unitCellOrigin[0]
                        unitCellDimensions.y = (gridSpacings[1]*gridDimensions[1])+unitCellOrigin[1]
                        unitCellDimensions.z = (gridSpacings[2]*gridDimensions[2])+unitCellOrigin[2]
                        electronicDensity = FloatArray(gridDimensions[0]*gridDimensions[1]*gridDimensions[2])
                    }
                    // Parsing atomic positions.
                    if (counter < 6+numberOfAtoms){
                        val lineContent = (line.trim().split(whiteSpaceRegex))
                        atomicPositions[counter-6] = Vector3f(lineContent[2].toFloat(), lineContent[3].toFloat(),
                            lineContent[4].toFloat())
                    }

                    // Parsing volumetric data.
                    // A possible optimization here would be to read this into a 1D array. We cannot directly
                    // read it into the byte buffer, because we don't know max/min values a-priori.
                    if (counter >= 6+numberOfAtoms) {
                        if (cubeStyle == "QE") {
                            val lineContent = line.trim().split(" ")
                            for (i in 0..lineContent.size step 2){
                                // Cube files should be in Fortran (z-fastest ordering).
                                // Kotlin is x-fastest ordering, so we have to convert that.
                                val floatVal = lineContent[i].toFloat()
                                electronicDensity[xcounter+ycounter*gridDimensions[0]+zcounter*gridDimensions[1]*gridDimensions[0]] = floatVal
                                if (floatVal > maxDensity) {
                                    maxDensity = floatVal
                                }
                                if (floatVal < minDensity) {
                                    minDensity = floatVal
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
                        } else {
                            val lineContent = (line.trim().split(whiteSpaceRegex))
                            for (value in lineContent) {
                                // Cube files should be in Fortran (z-fastest ordering).
                                // Kotlin is x-fastest ordering, so we have to convert that.
                                val floatVal = value.toFloat()
                                electronicDensity[xcounter+ycounter*gridDimensions[0]+zcounter*gridDimensions[1]*gridDimensions[0]] = floatVal
                                if (floatVal > maxDensity) {
                                    maxDensity = floatVal
                                }
                                if (floatVal < minDensity) {
                                    minDensity = floatVal
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
            }
            counter++
        }
        // Converting to byte buffer.
        counter = 0
        if (normalizeDensityTo > 0.0f){
            maxDensity = normalizeDensityTo
        }
        // Working on a temporary buffer, in case someone is accessing the buffer while we are still parsing.
        electronicDensityMemory = gridDimensions[0]*
            gridDimensions[1]*gridDimensions[2]*1
        electronicDensityUByte =  MemoryUtil.memAlloc((electronicDensityMemory))
        val tmpElectronicDensityUByte: ByteBuffer =  electronicDensityUByte.duplicate()
        for (z in 0 until gridDimensions[2]){
            for (y in 0 until gridDimensions[1]){
                for (x in 0 until gridDimensions[0]){
                    val value = (((electronicDensity[x+y*gridDimensions[0]+z*gridDimensions[1]*gridDimensions[0]] - minDensity) / (maxDensity - minDensity)) * 255.0f).toInt()
                    tmpElectronicDensityUByte.put(value.toByte())
                    counter++
                }
            }
        }
    }
}
