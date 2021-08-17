@file:Suppress("ClassName", "SpellCheckingInspection", "PropertyName", "EnumEntryName")

package graphics.scenery.utils

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.*
import com.sun.jna.win32.StdCallLibrary
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.backends.vulkan.toHexString
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Platform
import java.nio.ByteBuffer

interface NDI: StdCallLibrary {

    fun NDIlib_initialize(): Boolean
    fun NDIlib_destroy(): Unit
    fun NDIlib_version(): String
    fun NDIlib_send_create(): Pointer
    fun NDIlib_send_create(description: NDIlib_send_create_t.ByReference): Pointer
    fun NDIlib_send_destroy(send: Pointer): Unit
    fun NDIlib_send_send_video_v2(send: Pointer, frame: NDIlib_video_frame_v2_t.ByReference)

    companion object {
        val instance = Native.load(System.getProperty("scenery.NDIPublisher.LibraryPath", "") + getNativeLibraryName(), NDI::class.java) as NDI

        fun getNativeLibraryName(platform: Platform = Platform.get()): String {
            return when(platform) {
                Platform.LINUX -> "libndi.so"
                Platform.MACOSX -> "libndi.dylib"
                Platform.WINDOWS -> "Processing.NDI.Lib.x64.dll"
            }
        }
    }
}

open class NDIlib_send_create_t: Structure() {
    @JvmField var p_ndi_name: String = ""
    @JvmField var p_groups: String = ""

    @JvmField var clock_video: Boolean = true
    @JvmField var clock_audio: Boolean = true

    override fun getFieldOrder(): MutableList<String> {
        return mutableListOf(
            "p_ndi_name",
            "p_groups",
            "clock_video",
            "clock_audio"
        )
    }

    class ByReference : NDIlib_send_create_t(), Structure.ByReference

    class ByValue : NDIlib_send_create_t(), Structure.ByValue
}

open class NDIlib_video_frame_v2_t: Structure() {
    @JvmField var xres: Int = 0
    @JvmField var yres: Int = 0

    @JvmField var FourCC: Int = NDIPublisher.ndiFourCC('B', 'G', 'R', 'X')

    @JvmField var frame_rate_N: Int = 30000
    @JvmField var frame_rate_D: Int = 1001

    @JvmField var picture_aspect_ratio: Float = 0.0f

    @JvmField var frame_format_type: Int = NDIlib_frame_format_type_e.NDIlib_frame_format_type_progressive

    @JvmField var timecode: Long = Long.MAX_VALUE

    @JvmField var p_data: Pointer = Pointer(0)

    @JvmField var line_stride_in_bytes: Int = 0
    @JvmField var data_size_in_bytes: Int = 0

    @JvmField var p_metadata: String = ""

    @JvmField var timestamp: Long = 0

    override fun getFieldOrder(): MutableList<String> {
        return mutableListOf(
            "xres",
            "yres",
            "FourCC",
            "frame_rate_N",
            "frame_rate_D",
            "picture_aspect_ratio",
            "frame_format_type",
            "timecode",
            "p_data",
            "line_stride_in_bytes",
            "data_size_in_bytes",
            "p_metadata",
            "timestamp"
        )
    }

    class ByReference : NDIlib_video_frame_v2_t(), Structure.ByReference

    class ByValue : NDIlib_video_frame_v2_t(), Structure.ByValue
}

interface NDIlib_frame_format_type_e {
    companion object {
        const val NDIlib_frame_format_type_progressive = 1

        // A fielded frame with the field 0 being on the even lines and field 1 being
        // on the odd lines/
        const val NDIlib_frame_format_type_interleaved = 0

        // Individual fields
        const val NDIlib_frame_format_type_field_0 = 2
        const val NDIlib_frame_format_type_field_1 = 3
        // Ensure that the size is 32bits
        const val NDIlib_frame_type_max = 0x7fffffff
    }
}


open class NDIPublisher(
    val frameWidth: Int,
    val frameHeight: Int,
    val fps: Int = 60,
    final override var hub: Hub?): Hubable, AutoCloseable {
    private val logger by LazyLogger()

    protected val send: Pointer
    protected val desc = NDIlib_send_create_t.ByReference()
    protected val frame = NDIlib_video_frame_v2_t.ByReference()
    protected var memory = Memory(frameWidth * frameHeight * 4L)

    protected var initialised = false

    init {
        NDI.instance.NDIlib_initialize()
        logger.info("NDI interface initialised, NDI version: ${NDI.instance.NDIlib_version()}")

        desc.p_ndi_name = "scenery NDI output"
        hub?.getApplication()?.let { desc.p_ndi_name += " (${it.applicationName})" }

        send = NDI.instance.NDIlib_send_create(desc)
        logger.debug("NDI send created, ${desc.p_ndi_name} ($send)")

        frame.xres = frameWidth
        frame.yres = frameHeight
        frame.frame_rate_N = fps * 10000
        frame.frame_rate_D = 1000
        frame.FourCC = ndiFourCC('B', 'G', 'R', 'A')
        logger.debug("Sent FourCC ${frame.FourCC}")
        frame.line_stride_in_bytes = frame.xres*4
        frame.p_data = memory

        logger.debug("NDI initialised.")
        initialised = true
    }

    fun sendFrame(frameData: ByteArray) {
        val expectedSize = frameWidth * frameHeight * 4
        if(frameData.size != expectedSize) {
            throw IllegalStateException("Frame data was expected to have $expectedSize bytes, has ${frameData.size}")
        }

        memory.write(0, frameData, 0, frameData.size)
        frame.p_data = memory
        NDI.instance.NDIlib_send_send_video_v2(send, frame)
    }

    fun sendFrame(frameData: Pointer) {
        frame.p_data = frameData
        NDI.instance.NDIlib_send_send_video_v2(send, frame)
    }

    fun sendFrame(frameData: ByteBuffer) {
        val data = frameData.duplicate()
        val expectedSize = frameWidth * frameHeight * 4
        if(data.remaining() != expectedSize) {
            throw IllegalStateException("Frame data was expected to have $expectedSize bytes, has ${data.remaining()}")
        }

        frame.p_data = Pointer(MemoryUtil.memAddress(data, 0))
        NDI.instance.NDIlib_send_send_video_v2(send, frame)
    }

    override fun close() {
        if(initialised) {
            NDI.instance.NDIlib_send_destroy(send)
            logger.debug("NDI send destroyed.")
            NDI.instance.NDIlib_destroy()
            logger.debug("NDI instance closed.")
        }
    }

    companion object {
        fun ndiFourCC(a: Char, b: Char, c: Char, d: Char): Int = a.code or (b.code shl 8) or (c.code shl 16) or (d.code shl 24)
    }
}
