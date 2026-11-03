package graphics.scenery.textures

import net.imglib2.type.numeric.NumericType
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.joml.Vector3i
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

class UpdatableTexture(
    dimensions: Vector3i,
    channels: Int = 4,
    type: NumericType<*> = UnsignedByteType(),
    contents: ByteBuffer?,
    repeatUVW: Triple<RepeatMode, RepeatMode, RepeatMode> = Triple(RepeatMode.Repeat, RepeatMode.Repeat, RepeatMode.Repeat),
    borderColor: BorderColor = BorderColor.TransparentBlack,
    normalized: Boolean = true,
    mipmap: Boolean = true,
    minFilter: FilteringMode = FilteringMode.Linear,
    maxFilter: FilteringMode = FilteringMode.Linear,
    usageType: HashSet<UsageType> = hashSetOf(UsageType.Texture),
) : Texture(dimensions, channels, type, contents, repeatUVW, borderColor, normalized, mipmap, minFilter, maxFilter, usageType) {
    /** Data class for encapsulating partial transfers. */
    data class TextureExtents(val x: Int, val y: Int, val z: Int, val w: Int, val h: Int, val d: Int)

    /** Update class for partial updates. */
    data class TextureUpdate(val extents: TextureExtents, val contents: ByteBuffer, var consumed: Boolean = false, var deallocate: Boolean = false)

    /** List of [TextureUpdate]s for the currently active texture. */
    private var updates: CopyOnWriteArrayList<TextureUpdate> = CopyOnWriteArrayList()

    fun addUpdate(update: TextureUpdate) {
        updates.add(update)
        updated = System.nanoTime()
    }

    /** Returns true if the generic texture does have any non-consumed updates */
    fun hasConsumableUpdates(): Boolean {
        return updates.any { !it.consumed }
    }

    fun getConsumableUpdates(): List<TextureUpdate> {
        return updates.filter { !it.consumed }
    }

    /** Clears all consumed updates */
    fun clearConsumedUpdates() {
        updates.forEach {
            if((it.consumed && it.deallocate)) {
                MemoryUtil.memFree(it.contents)
            }
        }
        updates.removeIf { it.consumed }
    }

    /** Clears all updates */
    fun clearUpdates() {
        updates.clear()
    }
}
