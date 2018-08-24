package graphics.scenery.volumes.bdv

import cleargl.GLTypeEnum
import cleargl.GLVector
import coremem.enums.NativeTypeEnum
import graphics.scenery.*
import graphics.scenery.volumes.Volume
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.*

/**
 * Volume Rendering Node for scenery.
 * If [autosetProperties] is true, the node will automatically determine
 * the volumes' transfer function range.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Martin Weigert <mweigert@mpi-cbg.de>
 */
@Suppress("unused")
open class BDVVolume : Volume(false) {
    data class VolumeDescriptor(val path: Path?,
                                val width: Long,
                                val height: Long,
                                val depth: Long,
                                val dataType: NativeTypeEnum,
                                val bytesPerVoxel: Int,
                                val data: ByteBuffer)

    /**
     * Histogram class.
     */
    class Histogram<T : Comparable<T>>(histogramSize: Int) {
        /** Bin storage for the histogram. */
        val bins: HashMap<T, Long> = HashMap(histogramSize)

        /** Adds a new value, putting it in the corresponding bin. */
        fun add(value: T) {
            bins[value] = (bins[value] ?: 0L) + 1L
        }

        /** Returns the minimum value contained in the histogram. */
        fun min(): T = bins.keys.minBy { it } ?: (0 as T)
        /** Returns the maximum value contained in the histogram. */
        fun max(): T = bins.keys.maxBy { it } ?: (0 as T)
    }

    /**
     *  The rendering method used in the shader, can be
     *
     *  0 -- Local Maximum Intensity Projection
     *  1 -- Maximum Intensity Projection
     *  2 -- Alpha compositing
     */

    var context = SceneryContext(this)
        protected set

    init {
        // fake geometry
        this.vertices = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f))

        this.normals = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f))

        this.texcoords = BufferUtils.allocateFloatAndPut(
            floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f))

        this.indices = BufferUtils.allocateIntAndPut(
            intArrayOf(0, 1, 2, 0, 2, 3))

        this.geometryType = GeometryType.TRIANGLES
        this.vertexSize = 3
        this.texcoordSize = 2

        material = ShaderMaterial(context.factory)

        material.cullingMode = Material.CullingMode.None
        material.blending.transparent = true
        material.blending.sourceColorBlendFactor = Blending.BlendFactor.One
        material.blending.destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.sourceAlphaBlendFactor = Blending.BlendFactor.One
        material.blending.destinationAlphaBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        material.blending.colorBlending = Blending.BlendOp.add
        material.blending.alphaBlending = Blending.BlendOp.add

        colormaps["grays"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-grays.png").file)
        colormaps["hot"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-hot.png").file)
        colormaps["jet"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-jet.png").file)
        colormaps["plasma"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-plasma.png").file)
        colormaps["viridis"] = Colormap.ColormapFile(Volume::class.java.getResource("colormap-viridis.png").file)

        assignEmptyVolumeTexture()
    }

    override fun preDraw() {
        if(transferFunction.stale) {
            logger.debug("Transfer function is stale, updating")
            material.transferTextures["transferFunction"] = GenericTexture(
                "transferFunction", GLVector(transferFunction.textureSize.toFloat(), transferFunction.textureHeight.toFloat(), 1.0f),
                channels = 1, type = GLTypeEnum.Float, contents = transferFunction.serialise())

            material.textures["diffuse"] = "fromBuffer:transferFunction"
            material.needsTextureReload = true

            time = System.nanoTime().toFloat()
        }
    }
}
