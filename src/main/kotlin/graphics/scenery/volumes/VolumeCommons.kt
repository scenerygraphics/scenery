package graphics.scenery.volumes

import bdv.tools.transformation.TransformedSource
import graphics.scenery.*
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import org.slf4j.Logger
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.streams.toList

class VolumeCommons (val windowWidth: Int, val windowHeight: Int, val dataset: String, val logger: Logger) {

    val num_parts = when (dataset) {
        "Kingsnake" -> {
            1
        }
        "Rayleigh_Taylor" -> {
            2
        }
        "Beechnut" -> {
            3
        }
        "Simulation" -> {
            5
        }
        else -> {
            1
        }
    }

    val volumeDims = when (dataset) {
        "Kingsnake" -> {
            Vector3f(1024f, 1024f, 795f)
        }
        "Rayleigh_Taylor" -> {
            Vector3f(1024f, 1024f, 1024f)
        }
        "Beechnut" -> {
            Vector3f(1024f, 1024f, 1546f)
        }
        "Simulation" -> {
            Vector3f(2048f, 2048f, 1920f)
        }
        else -> {
            Vector3f(256f, 256f, 109f)
        }
    }

    val is16bit = if(dataset == "Kingsnake" || dataset == "Simulation") {
        false
    } else if (dataset == "Beechnut" || dataset == "Rayleigh_Taylor") {
        true
    } else {
        true
    }

    /**
     * Reads raw volumetric data from a [file].
     *
     * Returns the new volume.
     *
     * Based on Volume.fromPathRaw
     */
    fun fromPathRaw(file: Path, is16bit: Boolean = true, hub: Hub): BufferedVolume {

        val infoFile: Path
        val volumeFiles: List<Path>

        if(Files.isDirectory(file)) {
            volumeFiles = Files.list(file).filter { it.toString().endsWith(".raw") && Files.isRegularFile(it) && Files.isReadable(it) }.toList()
            infoFile = file.resolve("stacks.info")
        } else {
            volumeFiles = listOf(file)
            infoFile = file.resolveSibling("stacks.info")
        }

        val lines = Files.lines(infoFile).toList()

        logger.debug("reading stacks.info (${lines.joinToString()}) (${lines.size} lines)")
        val dimensions = Vector3i(lines.get(0).split(",").map { it.toInt() }.toIntArray())
        logger.debug("setting dim to ${dimensions.x}/${dimensions.y}/${dimensions.z}")
        logger.debug("Got ${volumeFiles.size} volumes")

        val volumes = CopyOnWriteArrayList<BufferedVolume.Timepoint>()
        volumeFiles.forEach { v ->
            val id = v.fileName.toString()
            val buffer: ByteBuffer by lazy {

                logger.debug("Loading $id from disk")
                val buffer = ByteArray(1024 * 1024)
                val stream = FileInputStream(v.toFile())
                val numBytes = if(is16bit) {
                    2
                } else {
                    1
                }
                val imageData: ByteBuffer = MemoryUtil.memAlloc((numBytes * dimensions.x * dimensions.y * dimensions.z))

                logger.debug("${v.fileName}: Allocated ${imageData.capacity()} bytes for image of $dimensions containing $numBytes per voxel")

                val start = System.nanoTime()
                var bytesRead = stream.read(buffer, 0, buffer.size)
                while (bytesRead > -1) {
                    imageData.put(buffer, 0, bytesRead)
                    bytesRead = stream.read(buffer, 0, buffer.size)
                }
                val duration = (System.nanoTime() - start) / 10e5
                logger.debug("Reading took $duration ms")

                imageData.flip()
                imageData
            }

            volumes.add(BufferedVolume.Timepoint(id, buffer))
        }

        return if(is16bit) {
            Volume.fromBuffer(volumes, dimensions.x, dimensions.y, dimensions.z, UnsignedShortType(), hub)
        } else {
            Volume.fromBuffer(volumes, dimensions.x, dimensions.y, dimensions.z, UnsignedByteType(), hub)
        }
    }

    fun positionCamera(cam: Camera) {
        with(cam) {
            spatial {
                if(dataset == "Kingsnake") {
                    position = Vector3f( 4.622E+0f, -9.060E-1f, -1.047E+0f) //V1 for kingsnake
                    rotation = Quaternionf( 5.288E-2, -9.096E-1, -1.222E-1,  3.936E-1)
                } else if (dataset == "Beechnut") {
                    position = Vector3f(-2.607E+0f, -5.973E-1f,  2.415E+0f) // V1 for Beechnut
                    rotation = Quaternionf(-9.418E-2, -7.363E-1, -1.048E-1, -6.618E-1)
                } else if (dataset == "Simulation") {
                    position = Vector3f(2.041E-1f, -5.253E+0f, -1.321E+0f) //V1 for Simulation
                    rotation = Quaternionf(9.134E-2, -9.009E-1,  3.558E-1, -2.313E-1)
                } else if (dataset == "Rayleigh_Taylor") {
                    position = Vector3f( -2.300E+0f, -6.402E+0f,  1.100E+0f) //V1 for Rayleigh_Taylor
                    rotation = Quaternionf(2.495E-1, -7.098E-1,  3.027E-1, -5.851E-1)
                } else if (dataset == "BonePlug") {
                    position = Vector3f( 1.897E+0f, -5.994E-1f, -1.899E+0f) //V1 for Boneplug
                    rotation = Quaternionf( 5.867E-5,  9.998E-1,  1.919E-2,  4.404E-3)
                } else if (dataset == "Rotstrat") {
                    position = Vector3f( 2.799E+0f, -6.156E+0f, -2.641E+0f) //V1 for Rotstrat
                    rotation = Quaternionf(-3.585E-2, -9.257E-1,  3.656E-1,  9.076E-2)
                } else if (dataset == "Isotropic") {
                    position = Vector3f( 2.799E+0f, -6.156E+0f, -2.641E+0f) //V1 for Isotropic
                    rotation = Quaternionf(-3.585E-2, -9.257E-1,  3.656E-1,  9.076E-2)
                }

//                position = Vector3f( 3.183E+0f, -5.973E-1f, -1.475E+0f) //V2 for Beechnut
//                rotation = Quaternionf( 1.974E-2, -9.803E-1, -1.395E-1,  1.386E-1)
//
//                position = Vector3f( 4.458E+0f, -9.057E-1f,  4.193E+0f) //V2 for Kingsnake
//                rotation = Quaternionf( 1.238E-1, -3.649E-1,-4.902E-2,  9.215E-1)

//                position = Vector3f( 6.284E+0f, -4.932E-1f,  4.787E+0f) //V2 for Simulation
//                rotation = Quaternionf( 1.162E-1, -4.624E-1, -6.126E-2,  8.769E-1)
            }
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            cam.farPlaneDistance = 20.0f
        }
    }

    fun setupTransferFunction() : TransferFunction {
        val tf = TransferFunction()
        with(tf) {
            if(dataset == "Stagbeetle" || dataset == "Stagbeetle_divided") {
                addControlPoint(0.0f, 0.0f)
                addControlPoint(0.005f, 0.0f)
                addControlPoint(0.01f, 0.3f)
            } else if (dataset == "Kingsnake") {
                addControlPoint(0.0f, 0.0f)
                addControlPoint(0.43f, 0.0f)
                addControlPoint(0.5f, 0.5f)
            } else if (dataset == "Beechnut") {
                addControlPoint(0.0f, 0.0f)
                addControlPoint(0.43f, 0.0f)
                addControlPoint(0.457f, 0.321f)
                addControlPoint(0.494f, 0.0f)
                addControlPoint(1.0f, 0.0f)
            } else if (dataset == "Simulation") {
                addControlPoint(0.0f, 0f)
                addControlPoint(0.1f, 0.0f)
                addControlPoint(0.15f, 0.65f)
                addControlPoint(0.22f, 0.15f)
                addControlPoint(0.28f, 0.0f)
                addControlPoint(0.49f, 0.0f)
                addControlPoint(0.7f, 0.95f)
                addControlPoint(0.75f, 0.8f)
                addControlPoint(0.8f, 0.0f)
                addControlPoint(0.9f, 0.0f)
                addControlPoint(1.0f, 0.0f)
            } else if (dataset == "Rayleigh_Taylor") {
                addControlPoint(0.0f, 0.95f)
                addControlPoint(0.15f, 0.0f)
                addControlPoint(0.45f, 0.0f)
                addControlPoint(0.5f, 0.35f)
                addControlPoint(0.55f, 0.0f)
                addControlPoint(0.80f, 0.0f)
                addControlPoint(1.0f, 0.378f)
            } else if (dataset == "Microscopy") {
                addControlPoint(0.0f, 0.0f)
                addControlPoint(0.5f, 0.0f)
                addControlPoint(0.65f, 0.0f)
                addControlPoint(0.80f, 0.2f)
                addControlPoint(0.85f, 0.0f)
            } else if (dataset == "Rotstrat") {
                TransferFunction.ramp(0.0025f, 0.005f, 0.7f)
            } else {
                logger.info("Using a standard transfer function")
                addControlPoint(0.0f, 0.0f)
                addControlPoint(1.0f, 1.0f)

            }
        }
        return tf
    }

    fun setColorMap(volume: BufferedVolume) {
        volume.name = "volume"
        volume.colormap = Colormap.get("hot")
        if(dataset == "Rotstrat") {
            volume.colormap = Colormap.get("jet")
            volume.converterSetups[0].setDisplayRange(25000.0, 50000.0)
        } else if(dataset == "Beechnut") {
            volume.converterSetups[0].setDisplayRange(0.0, 33465.0)
        } else if(dataset == "Simulation") {
            volume.colormap = Colormap.get("rb")
            volume.converterSetups[0].setDisplayRange(50.0, 205.0)
        } else if(dataset == "Rayleigh_Taylor") {
            volume.colormap = Colormap.get("rbdarker")
        }
    }

    fun setupVolumes(volumeList: ArrayList<BufferedVolume>, tf: TransferFunction, hub: Hub): RichNode {
        val datasetPath = Paths.get("/home/aryaman/Datasets/Volume/${dataset}")
//        val datasetPath = Paths.get("/scratch/ws/1/argupta-distributed_vdis/Datasets/${dataset}")


        val pixelToWorld = (0.0075f * 512f) / volumeDims.x

        val parent = RichNode()

        var prev_slices = 0f
        var current_slices = 0f

        var prevIndex = 0f

        for(i in 1..num_parts) {
            val volume = fromPathRaw(Paths.get("$datasetPath/Part$i"), is16bit, hub)
            volume.name = "volume"
            volume.colormap = Colormap.get("hot")
            if(dataset == "Rotstrat") {
                volume.colormap = Colormap.get("jet")
                volume.converterSetups[0].setDisplayRange(25000.0, 50000.0)
            } else if(dataset == "Beechnut") {
                volume.converterSetups[0].setDisplayRange(0.0, 33465.0)
            } else if(dataset == "Simulation") {
                volume.colormap = Colormap.get("rb")
                volume.converterSetups[0].setDisplayRange(50.0, 205.0)
            } else if(dataset == "Rayleigh_Taylor") {
                volume.colormap = Colormap.get("rbdarker")
            }

            volume.origin = Origin.FrontBottomLeft
            val source = (volume.ds.sources[0].spimSource as TransformedSource).wrappedSource as? BufferSource<*>
            //volume.converterSetups.first().setDisplayRange(10.0, 220.0)
            current_slices = source!!.depth.toFloat()
            logger.info("current slices: $current_slices")
            volume.pixelToWorldRatio = pixelToWorld
            volume.transferFunction = tf

//            if(i == 2){
//                val tfUI = TransferFunctionUI(650, 500, volume)
//            }

//            volume.spatial().position = Vector3f(2.0f, 6.0f, 4.0f - ((i - 1) * ((volumeDims.z / num_parts) * pixelToWorld)))
//            if(i > 1) {
//            val temp = Vector3f(volumeList.lastOrNull()?.spatial()?.position?: Vector3f(0f)) - Vector3f(0f, 0f, (prev_slices/2f + current_slices/2f) * pixelToWorld)
            val temp = Vector3f(0f, 0f, 1.0f * (prevIndex) * pixelToWorld)
            volume.spatial().position = temp
//            if(num_parts > 1) {
//                volume.spatial().scale = Vector3f(1.0f, 1.0f, 2.0f)
//            }
//            }
            prevIndex += current_slices
            logger.info("volume slice $i position set to $temp")
            prev_slices = current_slices
//            volume.spatial().updateWorld(true)
            parent.addChild(volume)
//            println("Volume model matrix is: ${Matrix4f(volume.spatial().model).invert()}")
            volumeList.add(volume)
        }

//        parent.spatial().position = Vector3f(2.0f, 6.0f, 4.0f) - Vector3f(0.0f, 0.0f, volumeList.map { it.spatial().position.z }.sum()/2.0f)
        parent.spatial().position = Vector3f(0f)
//        cam.spatial().position = Vector3f(0f)

        val middle_index = if(num_parts % 2 == 0) {
            (num_parts / 2) - 1
        } else {
            (num_parts + 1) / 2 - 1
        }

        return parent
    }

    fun setupPivot(parent: RichNode): Box {
        val pivot = Box(Vector3f(20.0f))
        pivot.material().diffuse = Vector3f(0.0f, 1.0f, 0.0f)
        pivot.spatial().position = Vector3f(volumeDims.x/2.0f, volumeDims.y/2.0f, volumeDims.z/2.0f)
        parent.children.first().addChild(pivot)
        parent.spatial().updateWorld(true)
        return pivot
    }

}
