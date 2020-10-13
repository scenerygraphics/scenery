package graphics.scenery.utils

import ij.IJ
import ij.ImagePlus
import io.scif.SCIFIO
import io.scif.SCIFIOService
import io.scif.img.ImgUtilityService
import io.scif.img.converters.PlaneConverterService
import io.scif.services.DatasetIOService
import io.scif.services.InitializeService
import net.imglib2.img.Img
import net.imglib2.img.display.imagej.ImageJFunctions
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import org.janelia.saalfeldlab.n5.GzipCompression
import org.janelia.saalfeldlab.n5.N5FSWriter
import org.janelia.saalfeldlab.n5.imglib2.N5Utils
import org.scijava.Context
import org.scijava.app.StatusService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class N5Converter {
    companion object {
        @JvmStatic
        fun convert(input: Path, output: Path, datasetName: String) {
            val context = Context(DatasetIOService::class.java, ImgUtilityService::class.java, StatusService::class.java, PlaneConverterService::class.java, SCIFIOService::class.java, InitializeService::class.java)
            val dsio = context.getService(DatasetIOService::class.java)
//            val imp: ImagePlus = IJ.openImage(input.toString())
            val dataset = dsio.open(input.toString())
            val img = dataset.typedImg(FloatType()).img
//            val img: Img<FloatType> = ImageJFunctions.wrapFloat(imp)

            if(!output.toFile().exists() || !output.toFile().isDirectory) {
                Files.createDirectory(output)
            }

            val n5 = N5FSWriter(output.toString())
            N5Utils.save(img, n5, datasetName, intArrayOf(img.dimension(0).toInt(), img.dimension(1).toInt(), img.dimension(2).toInt()), GzipCompression())
        }
    }
}

fun main(args: Array<String>) {
    if(args.size < 3) {
        System.err.println("Not enough arguments! Run as N5Converter inputFile outputDirectory datasetName")
        exitProcess(-1)
    }

    val input = args[0]
    val output = args[1]
    val datasetName = args[2]

    println("scenery N5 converter: Converting $input to N5 at $output, name: $datasetName")

    val inputPath = Paths.get(input)
    val outputPath = Paths.get(output)

    N5Converter.convert(inputPath, outputPath, datasetName)

    println("Conversion done.")
}
