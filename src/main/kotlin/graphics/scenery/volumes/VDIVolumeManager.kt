package graphics.scenery.volumes

import graphics.scenery.Hub
import graphics.scenery.RichNode
import graphics.scenery.Scene
import graphics.scenery.ShaderMaterial
import graphics.scenery.backends.Shaders
import graphics.scenery.compute.ComputeMetadata
import graphics.scenery.compute.InvocationType
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.real.FloatType
import org.joml.Vector3f
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import tpietzsch.shadergen.generate.SegmentTemplate
import tpietzsch.shadergen.generate.SegmentType

class VDIVolumeManager ( var hub: Hub, val windowWidth: Int, val windowHeight: Int, val maxSupersegments: Int,val scene: Scene, val vdiFull: Boolean = true)

{
    fun createVDIVolumeManger() : VolumeManager {
        if (vdiFull)
            return vdiFull(windowWidth, windowHeight, maxSupersegments, scene, hub)
        else
            return vdiFull(windowWidth, windowHeight, maxSupersegments, scene, hub)
    }

    private fun instantiateVolumeManager(raycastShader: String, accumulateShader: String, hub: Hub): VolumeManager {

        return VolumeManager(
            hub, useCompute = true,
            customSegments = hashMapOf(
                SegmentType.FragmentShader to SegmentTemplate(
                    this::class.java,
                    raycastShader,
                    "intersectBoundingBox", "vis", "localNear", "localFar", "SampleVolume", "Convert", "Accumulate",
                ),
                SegmentType.Accumulator to SegmentTemplate(
                    accumulateShader,
                    "vis", "localNear", "localFar", "sampleVolume", "convert",
                ),
            ),
        )
    }

    private fun vdiFull(windowWidth: Int, windowHeight: Int, maxSupersegments: Int, scene: Scene, hub: Hub): VolumeManager {
        val raycastShader = "VDIGenerator.comp"
        val accumulateShader = "AccumulateVDI.comp"
        val volumeManager = instantiateVolumeManager(raycastShader, accumulateShader, hub)

        val outputSubColorBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*4*maxSupersegments * 4)

        val outputSubDepthBuffer = MemoryUtil.memCalloc(windowHeight*windowWidth*2*maxSupersegments*2 * 2)

        val numGridCells = Vector3f(windowWidth.toFloat() / 8f, windowHeight.toFloat() / 8f, maxSupersegments.toFloat())

        val lowestLevel = MemoryUtil.memCalloc(numGridCells.x.toInt() * numGridCells.y.toInt() * numGridCells.z.toInt() * 4)

        val outputSubVDIColor: Texture = Texture.fromImage(
            Image(outputSubColorBuffer, maxSupersegments, windowHeight, windowWidth), usage = hashSetOf( Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), channels = 4, mipmap = false,  normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add("OutputSubVDIColor")
        volumeManager.material().textures["OutputSubVDIColor"] = outputSubVDIColor

        val outputSubVDIDepth: Texture = Texture.fromImage(
            Image(outputSubDepthBuffer, 2 * maxSupersegments, windowHeight, windowWidth),  usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture),
            type = FloatType(), channels = 1, mipmap = false, normalized = false, minFilter = Texture.FilteringMode.NearestNeighbour, maxFilter = Texture.FilteringMode.NearestNeighbour)
        volumeManager.customTextures.add("OutputSubVDIDepth")
        volumeManager.material().textures["OutputSubVDIDepth"] = outputSubVDIDepth

        val gridCells: Texture = Texture.fromImage(
            Image(lowestLevel, numGridCells.x.toInt(), numGridCells.y.toInt(), numGridCells.z.toInt()), channels = 1, type = UnsignedIntType(),
            usage = hashSetOf(Texture.UsageType.LoadStoreImage, Texture.UsageType.Texture))
        volumeManager.customTextures.add("OctreeCells")
        volumeManager.material().textures["OctreeCells"] = gridCells

        volumeManager.customUniforms.add("doGeneration")
        volumeManager.shaderProperties["doGeneration"] = true

        val compute = RichNode()
        compute.setMaterial(ShaderMaterial(Shaders.ShadersFromFiles(arrayOf("GridCellsToZero.comp"), this::class.java)))

        compute.metadata["ComputeMetadata"] = ComputeMetadata(
            workSizes = Vector3i(numGridCells.x.toInt(), numGridCells.y.toInt(), 1),
            invocationType = InvocationType.Permanent
        )

        compute.material().textures["GridCells"] = gridCells

        scene.addChild(compute)

        return volumeManager
    }


}

