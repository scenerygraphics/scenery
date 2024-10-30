package graphics.scenery.volumes

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStream

/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 *
 *     Interface to abstract out the basic parameters of a TransferFunction use case.
 */
interface HasTransferFunction {

    var transferFunction : TransferFunction
    var minDisplayRange : Float
    var maxDisplayRange : Float

    /**
     * The allowed value range for [minDisplayRange] and [maxDisplayRange]. Eg. 0 and Short.MAX_VALUE
     */
    var displayRangeLimits: Pair<Float, Float>

    /**
     * Load transfer function and display range from file that was written by [HasTransferFunction.saveTransferFunctionToFile]
     */
    fun loadTransferFunctionFromFile(file: File){
        val tf = TransferFunction()
        val inputStream: InputStream = file.inputStream()
        var isRangeSet = false
        inputStream.bufferedReader().forEachLine {
            val line = it.trim().split(";").mapNotNull(kotlin.String::toFloatOrNull)
            if (line.size == 2){
                if (!isRangeSet){
                    minDisplayRange = line[0]
                    maxDisplayRange = line[1]
                    isRangeSet = true
                } else {
                    tf.addControlPoint(line[0], line[1])
                }
            }
        }
        transferFunction = tf
    }

    /**
     * Write transfer function to file in a human-readable way.
     * Format:
     * First line is the display range sepaerated by a semicolon.
     * All following lines are tf control points.
     */
    fun saveTransferFunctionToFile(file: File){
        val writer = BufferedWriter(FileWriter(file))
        writer.write("${minDisplayRange};${maxDisplayRange}")
        writer.newLine()
        transferFunction.controlPoints().forEach {
            writer.write("${it.value};${it.factor}")
            writer.newLine()
        }
        writer.close()
    }
}
