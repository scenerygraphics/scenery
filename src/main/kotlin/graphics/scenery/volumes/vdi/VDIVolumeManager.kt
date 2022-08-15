package graphics.scenery.volumes.vdi

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import graphics.scenery.volumes.VolumeManager
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType

class VDIVolumeManager {

    companion object {
        fun create(generateVDIs: Boolean, separateDepth: Boolean, colors32bit: Boolean, scene: Scene, hub: Hub): VolumeManager {

            val settings = hub.get<Settings>()?: throw IllegalStateException("Settings instance required for creation of volume manager")

            val maxSupersegments = settings.get("VDI.maxSupersegments", 20)


            val raycastShader: String
            val accumulateShader: String

            val window = hub.get<Renderer>()?.window ?: throw IllegalStateException("Renderer and window need to be initialized before volume manager is created.")

            return if(generateVDIs) {
                raycastShader = "VDIGenerator.comp"
                accumulateShader = "AccumulateVDI.comp"
                val numLayers = if(separateDepth) {
                    1
                } else {
                    3         // VDI supersegments require both front and back depth values, along with color
                }

                val volumeManager = VolumeManager(
                    hub, useCompute = true, customSegments = hashMapOf(
                        SegmentType.FragmentShader to SegmentTemplate(
                            this::class.java,
                            raycastShader,
                            "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate",
                        ),
                        SegmentType.Accumulator to SegmentTemplate(
                                this::class.java,
                            accumulateShader,
                            "vis", "localNear", "localFar", "sampleVolume", "convert",
                        ),
                    ),
                )

                val outputSubColorBuffer = if(colors32bit) {
                    MemoryUtil.memCalloc(window.height*window.width*4*maxSupersegments*numLayers * 4)
                } else {
                    MemoryUtil.memCalloc(window.height*window.width*4*maxSupersegments*numLayers)
                }
                val outputSubDepthBuffer = if(separateDepth) {
                    MemoryUtil.memCalloc(window.height*window.width*2*maxSupersegments*2 * 2)
//                MemoryUtil.memCalloc(window.height*window.width*2*maxSupersegments*2)
                } else {
                    MemoryUtil.memCalloc(0)
                }

                val numGridCells = Vector3f(window.width.toFloat() / 8f, window.height.toFloat() / 8f, maxSupersegments.toFloat())
                val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

                val thresholdBuffer = MemoryUtil.memCalloc(window.width * window.height * 4)

                val outputSubVDIColor: Texture
                val outputSubVDIDepth: Texture
                val gridCells: Texture
                val thresholds: Texture

                outputSubVDIColor = if(colors32bit) {
                    Texture.fromImage(
                        Image(outputSubColorBuffer, numLayers * maxSupersegments, window.height, window.width), usage = hashSetOf(
                            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 4, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                } else {
                    Texture.fromImage(
                        Image(outputSubColorBuffer, numLayers * maxSupersegments, window.height, window.width), usage = hashSetOf(
                            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
                }

                volumeManager.customTextures.add("OutputSubVDIColor")
                volumeManager.material().textures["OutputSubVDIColor"] = outputSubVDIColor

                if(separateDepth) {
                    outputSubVDIDepth = Texture.fromImage(
                        Image(outputSubDepthBuffer, 2 * maxSupersegments, window.height, window.width),  usage = hashSetOf(
                            Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture), type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                    volumeManager.customTextures.add("OutputSubVDIDepth")
                    volumeManager.material().textures["OutputSubVDIDepth"] = outputSubVDIDepth
                }

                gridCells = Texture.fromImage(
                    Image(lowestLevel, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), channels = 1, type = UnsignedIntType(),
                    usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
                volumeManager.customTextures.add("OctreeCells")
                volumeManager.material().textures["OctreeCells"] = gridCells

                thresholds = Texture.fromImage(
                    Image(thresholdBuffer, window.width, window.height), usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
                    type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
                volumeManager.customTextures.add("Thresholds")
                volumeManager.material().textures["Thresholds"] = thresholds

                volumeManager.customUniforms.add("doGeneration")
                volumeManager.shaderProperties["doGeneration"] = 1

                hub.add(volumeManager)

                val compute = RichNode()
                compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("GridCellsToZero.comp"), this::class.java)))

                compute.metadata["ComputeMetadata"] = ComputeMetadata(
                    workSizes = Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), 1),
                    invocationType = InvocationType.Permanent
                )

                compute.material().textures["GridCells"] = gridCells

                scene.addChild(compute)

                volumeManager
            } else {
                val volumeManager = VolumeManager(hub,
                    useCompute = true,
                    customSegments = hashMapOf(
                        SegmentType.FragmentShader to SegmentTemplate(
                            this::class.java,
                            "ComputeRaycast.comp",
                            "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate"),
                    ))
                volumeManager.customTextures.add("OutputRender")

                val outputBuffer = MemoryUtil.memCalloc(window.width*window.height*4)
                val outputTexture = Texture.fromImage(
                    Image(outputBuffer, window.width, window.height), usage = hashSetOf(
                        Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
                volumeManager.material().textures["OutputRender"] = outputTexture

                hub.add(volumeManager)

                val plane = FullscreenObject()
                scene.addChild(plane)
                plane.material().textures["diffuse"] = volumeManager.material().textures["OutputRender"]!!

                volumeManager
            }

        }
    }
}
