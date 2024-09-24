package graphics.scenery.volumes

import bvv.core.blockmath.FindRequiredBlocks
import bvv.core.blockmath.MipmapSizes
import bvv.core.blockmath.RequiredBlocks
import bvv.core.blocks.TileAccess
import bvv.core.cache.*
import bvv.core.multires.MultiResolutionStack3D
import bvv.core.multires.ResolutionLevel3D
import bvv.core.render.LookupTextureARGB
import bvv.core.render.VolumeBlocks
import bvv.core.util.MatrixMath
import net.imglib2.Interval
import net.imglib2.util.LinAlgHelpers
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector3f
import java.util.ArrayList
import java.util.HashSet
import kotlin.math.max
import kotlin.math.min

class CustomVolumeBlocks(private val textureCache: TextureCache) : VolumeBlocks(textureCache) {
    private val cacheSpec: CacheSpec = textureCache.spec()
    private val lut: LookupTextureARGB = LookupTextureARGB()
    private var tileAccess: TileAccess.Cache = TileAccess.Cache()
    private val sizes: MipmapSizes = CustomMipmapSizes()

    private var multiResolutionStack: MultiResolutionStack3D<*>? = null

    /** `projection * view * model` matrix  */
    val pvm: Matrix4f = Matrix4f()

    /**
     * Chosen base resolution level for rendering the volume.
     * Every block in the volumes LUT is at this level or higher (coarser).
     */
    private var baseLevel = 0

    /**
     * Volume blocks at [.baseLevel] that should make it into the LUT (at `baseLevel` or higher).
     */
    private var requiredBlocks: RequiredBlocks? = null

    /**
     * @param multiResolutionStack single-channel, multi-resolution source
     * @param viewportWidth width of the surface to be rendered
     * @param pv `projection * view` matrix, transforms world coordinates to NDC coordinates
     */
    override fun init(
        multiResolutionStack: MultiResolutionStack3D<*>,
        viewportWidth: Int,
        pv: Matrix4fc?
    ) {
        this.multiResolutionStack = multiResolutionStack

        val model = MatrixMath.affine(multiResolutionStack.sourceTransform, Matrix4f())
        pvm.set(pv).mul(model)
        sizes.init(pvm, viewportWidth, multiResolutionStack.resolutions())
        baseLevel = sizes.baseLevel
    }

    /**
     * Get the base resolution level for rendering the volume.
     * Every block in the volumes LUT is at this level or higher (coarser).
     *
     *
     * This is chosen automatically, when calling [.init].
     * It can be manually adjusted using [.setBaseLevel].
     */
    override fun getBaseLevel(): Int {
        return baseLevel
    }

    /**
     * Set the base resolution level for rendering the volume.
     * Every block in the volumes LUT is at this level or higher (coarser).
     */
    override fun setBaseLevel(baseLevel: Int) {
        this.baseLevel = baseLevel
    }

    /**
     * Get the size of a voxel at base resolution in world coordinates.
     * Take a source voxel (0,0,0)-(1,1,1) at the
     * base mipmap level and transform it to world coordinates.
     * Take the minimum of the transformed voxels edge lengths.
     */
    override fun getBaseLevelVoxelSizeInWorldCoordinates(): Double {
        val sourceToWorld = multiResolutionStack!!.sourceTransform
        val r = multiResolutionStack!!.resolutions()[baseLevel].r

        val tzero = DoubleArray(3)
        sourceToWorld.apply(DoubleArray(3), tzero)

        val one = DoubleArray(3)
        val tone = DoubleArray(3)
        var voxelSize = Double.POSITIVE_INFINITY
        for(i in 0..2) {
            for(d in 0..2) one[d] = (if(d == i) r[d] else 0).toDouble()
            sourceToWorld.apply(one, tone)
            LinAlgHelpers.subtract(tone, tzero, tone)
            voxelSize = min(voxelSize, LinAlgHelpers.length(tone))
        }
        return voxelSize
    }

    /**
     * Sets up `RequiredBlocks` (internally) and creates a list of `FillTask`s for the cache to process.
     *
     * @return list of `FillTask`s
     */
    override fun getFillTasks(): List<FillTask> {
        // block coordinates are grid coordinates of baseLevel resolution
        requiredBlocks = getRequiredBlocks(baseLevel)
        assignBestLevels(requiredBlocks, baseLevel, baseLevel)
        val fillTasks = getFillTasks(requiredBlocks, baseLevel)
        return fillTasks
    }

    /**
     * @return whether every required block was completely available at the desired resolution level.
     * I.e., if `false` is returned, the frame should be repainted until the remaining incomplete blocks are loaded.
     */
    override fun makeLut(timestamp: Int): Boolean {
        val rmin = requiredBlocks!!.min
        val rmax = requiredBlocks!!.max
        lut.init(rmin, rmax, baseLevel)

        var complete = true
        val maxLevel = multiResolutionStack!!.resolutions().size - 1
        val r = multiResolutionStack!!.resolutions()[baseLevel].r
        val gj = IntArray(3)
        for(block in requiredBlocks!!.blocks) {
            val g0 = block.gridPos
            for(level in block.bestLevel..maxLevel) {
                val resolution = multiResolutionStack!!.resolutions()[level]
                val sj = resolution.s
                for(d in 0..2) gj[d] = (g0[d] * sj[d] * r[d]).toInt()
                val tile: TextureCache.Tile? =
                    textureCache.get(ImageBlockKey(resolution, gj))
                if(tile != null) {
                    tile.useAtTimestamp(timestamp)
                    lut.putTile(g0, tile, level)
                    if(level != block.bestLevel || tile.state() == TextureCache.ContentState.INCOMPLETE) complete =
                        false
                    break
                } else if(level == maxLevel) complete = false
            }
        }
        return complete
    }

    /**
     * Set up `lutBlockScales` array for shader.
     *
     *  * `lutBlockScales[0] = (0,0,0)` is used for oob blocks.
     *  * `lutBlockScales[i+1]` is the relative scale between `baseLevel` and level `baseLevel+i`
     *  * `lutBlockScales[i+1] = (0,0,0)` for `i > maxLevel` is used to fill up the array to `NUM_BLOCK_SCALES`
     *
     *
     * @param NUM_BLOCK_SCALES
     * @return
     */
    override fun getLutBlockScales(NUM_BLOCK_SCALES: Int): Array<FloatArray> {
        val lutBlockScales = Array(NUM_BLOCK_SCALES) {
            FloatArray(
                3
            )
        }

        val r = multiResolutionStack!!.resolutions()[baseLevel].r
        for(d in 0..2) lutBlockScales[0][d] = 0f

        val maxLevel = multiResolutionStack!!.resolutions().size - 1
        for(level in baseLevel..maxLevel) {
            val resolution = multiResolutionStack!!.resolutions()[level]
            val sj = resolution.s
            val i = 1 + level - baseLevel
            for(d in 0..2) lutBlockScales[i][d] = (sj[d] * r[d]).toFloat()
        }

        return lutBlockScales
    }

    // TODO: revise / remove
    override fun getIms(): Matrix4f {
        return MatrixMath.affine(multiResolutionStack!!.sourceTransform, Matrix4f()).mul(getUpscale(baseLevel))
            .invert()
    }

    // TODO: revise / remove
    override fun getSourceLevelMin(): Vector3f {
        val lbb: Interval = multiResolutionStack!!.resolutions()[baseLevel].image
        val sourceLevelMin = Vector3f(lbb.min(0).toFloat(), lbb.min(1).toFloat(), lbb.min(2).toFloat())
        return sourceLevelMin
    }

    // TODO: revise / remove
    override fun getSourceLevelMax(): Vector3f {
        val lbb: Interval = multiResolutionStack!!.resolutions()[baseLevel].image
        val sourceLevelMax = Vector3f(lbb.max(0).toFloat(), lbb.max(1).toFloat(), lbb.max(2).toFloat())
        return sourceLevelMax
    }

    override fun getLookupTexture(): LookupTextureARGB {
        return lut
    }


    /**
     * Get visible blocks in grid coordinates of `baseLevel` resolution.
     */
    private fun getRequiredBlocks(baseLevel: Int): RequiredBlocks {
        val pvms: Matrix4fc = pvm.mul(getUpscale(baseLevel), Matrix4f())
        val gridMin = LongArray(3)
        val gridMax = LongArray(3)
        getGridMinMax(baseLevel, gridMin, gridMax)
        return FindRequiredBlocks.getRequiredLevelBlocksFrustum(pvms, cacheSpec.blockSize(), gridMin, gridMax)
    }

    /**
     * Determine best resolution level for each block.
     * Block coordinates are grid coordinates of `baseLevel` resolution
     * Best resolution is capped at `minLevel`.
     * (`minLevel <= bestLevel`)
     */
    private fun assignBestLevels(requiredBlocks: RequiredBlocks?, baseLevel: Int, minLevel: Int) {
        val r = multiResolutionStack!!.resolutions()[baseLevel].r
        val blockSize = cacheSpec!!.blockSize()
        val scale = intArrayOf(
            blockSize[0] * r[0],
            blockSize[1] * r[1],
            blockSize[2] * r[2]
        )
        val blockCenter = Vector3f()
        val tmp = Vector3f()
        for(block in requiredBlocks!!.blocks) {
            val g0 = block.gridPos
            blockCenter[(g0[0] + 0.5f) * scale[0], (g0[1] + 0.5f) * scale[1]] = (g0[2] + 0.5f) * scale[2]
            val bestLevel =
                max(minLevel.toDouble(), sizes!!.bestLevel(blockCenter, tmp).toDouble()).toInt()
            block.bestLevel = bestLevel
        }
    }

    private fun getFillTasks(requiredBlocks: RequiredBlocks?, baseLevel: Int): List<FillTask> {
        val maxLevel = multiResolutionStack!!.resolutions().size - 1
        val r = multiResolutionStack!!.resolutions()[baseLevel].r
        val existingKeys = HashSet<ImageBlockKey<*>>()
        val fillTasks: MutableList<FillTask> = ArrayList()
        val gj = IntArray(3)
        for(block in requiredBlocks!!.blocks) {
            val g0 = block.gridPos
            for(level in block.bestLevel..maxLevel) {
                val resolution = multiResolutionStack!!.resolutions()[level]
                val sj = resolution.s
                for(d in 0..2) gj[d] = (g0[d] * sj[d] * r[d]).toInt()

                val key = ImageBlockKey(resolution, gj)
                if(!existingKeys.contains(key)) {
                    existingKeys.add(key)
                    val tile: TextureCache.Tile? = textureCache.get(key)
                    if(tile != null || canLoadCompletely(key) || level == maxLevel) {
                        fillTasks.add(
                            DefaultFillTask(key,
                                            { buf: UploadBuffer ->
                                                loadTile(
                                                    key,
                                                    buf
                                                )
                                            },
                                            { containsData(key) })
                        )
                        break
                    }
                } else break // TODO: is this always ok?
            }
        }

        return fillTasks
    }

    private fun canLoadCompletely(key: ImageBlockKey<ResolutionLevel3D<*>>): Boolean {
        return tileAccess[key.image(), cacheSpec].canLoadCompletely(key.pos(), false)
    }

    private fun containsData(key: ImageBlockKey<ResolutionLevel3D<*>>): Boolean {
        /*
     * TODO.
     *
     * ContainsData is called to determine whether it makes sense to upload a partially completed block.
     *
     * Using TileAccess.canLoadCompletely will not upload any partial block, saving on texture upload bandwidth.
     *
     * Using TileAccess.canLoadPartially will upload any partial blocks, consuming (typically much) more texture upload bandwidth,
     * but potentially presenting a more complete volume to the user.
     *
     * Decisions, decisions...
     */
        return tileAccess[key.image(), cacheSpec].canLoadCompletely(key.pos(), true)
//		return tileAccess.get( key.image(), cacheSpec ).canLoadPartially( key.pos() );
    }

    private fun loadTile(key: ImageBlockKey<ResolutionLevel3D<*>>, buffer: UploadBuffer): Boolean {
        return tileAccess[key.image(), cacheSpec].loadTile(key.pos(), buffer)
    }

    /**
     * Compute intersection of view frustum and source bounding box in grid coordinates of the specified resolutions `level`.
     *
     * @param level
     * resolution level
     */
    private fun getGridMinMax(level: Int, gridMin: LongArray, gridMax: LongArray) {
        val ipvms: Matrix4fc = pvm.mul(getUpscale(level), Matrix4f()).invert()

        val lbb: Interval = multiResolutionStack!!.resolutions()[level].image
        val fbbmin = Vector3f()
        val fbbmax = Vector3f()
        ipvms.frustumAabb(fbbmin, fbbmax)
        for(d in 0..2) {
            val lbbmin = lbb.min(d).toFloat() // TODO -0.5 offset?
            val lbbmax = lbb.max(d).toFloat() // TODO -0.5 offset?
            gridMin[d] = (max(fbbmin[d].toDouble(), lbbmin.toDouble()) / cacheSpec.blockSize()[d]).toLong()
            gridMax[d] = (min(fbbmax[d].toDouble(), lbbmax.toDouble()) / cacheSpec.blockSize()[d]).toLong()
        }
    }

    private fun getUpscale(level: Int): Matrix4f {
        return getUpscale(level, Matrix4f())
    }

    private fun getUpscale(level: Int, dest: Matrix4f): Matrix4f {
        val r = multiResolutionStack!!.resolutions()[level].r
        val bsx = r[0]
        val bsy = r[1]
        val bsz = r[2]
        return dest.set(
            bsx.toFloat(), 0f, 0f, 0f,
            0f, bsy.toFloat(), 0f, 0f,
            0f, 0f, bsz.toFloat(), 0f,
            0.5f * (bsx - 1), 0.5f * (bsy - 1), 0.5f * (bsz - 1), 1f
        )
    }
}