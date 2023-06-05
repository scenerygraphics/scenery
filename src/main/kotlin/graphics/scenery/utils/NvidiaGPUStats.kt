package graphics.scenery.utils

import com.sun.jna.Function
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.util.*

/**
 * Created by ulrik on 2/1/2017.
 */
interface NVAPI : StdCallLibrary {

    fun nvapi_QueryInterface(pointer: Long): Long

    companion object {
        val instance = Native.loadLibrary("nvapi64", NVAPI::class.java) as NVAPI
    }
}

class NvidiaGPUStats: GPUStats {
    private val logger by lazyLogger()

    override val utilisations: HashMap<String, Float> = HashMap()

    val NVAPI_MAX_USAGES_PER_GPU = 34
    val NVAPI_MAX_PHYSICAL_GPUS = 64

    val nvapi_initialize_pointer = 0x0150E828L
    val nvapi_enum_physical_gpus_pointer = 0xE5AC921FL
    val nvapi_gpu_get_usages_pointer = 0x189A1FDFL
    val nvapi_get_memory_info_pointer = 0x07F9B368L
    val nvapi_get_display_handle = 0x9ABDD40DL

    val NVAPI_Initialize: Function
    val NVAPI_EnumPhysicalGPUs: Function
    val NVAPI_GPUGetUsages: Function
    val NVAPI_GetMemoryInfo: Function
    val NVAPI_GetDisplayHandle: Function

    val gpuCount = IntByReference(0)
    val gpuHandles = IntArray(NVAPI_MAX_PHYSICAL_GPUS)
    val gpuUsages = IntArray(NVAPI_MAX_USAGES_PER_GPU, { 0 })
    val memoryInfo = IntArray(8, { 0 })

    private var failedTimesUsage = 0
    private var failedTimesMemory = 0

    init {
        NVAPI_Initialize = Function.getFunction(Pointer(NVAPI.instance.nvapi_QueryInterface(nvapi_initialize_pointer)))
        NVAPI_EnumPhysicalGPUs = Function.getFunction(Pointer(NVAPI.instance.nvapi_QueryInterface(nvapi_enum_physical_gpus_pointer)))
        NVAPI_GPUGetUsages = Function.getFunction(Pointer(NVAPI.instance.nvapi_QueryInterface(nvapi_gpu_get_usages_pointer)))
        NVAPI_GetMemoryInfo = Function.getFunction(Pointer(NVAPI.instance.nvapi_QueryInterface(nvapi_get_memory_info_pointer)))
        NVAPI_GetDisplayHandle = Function.getFunction(Pointer(NVAPI.instance.nvapi_QueryInterface(nvapi_get_display_handle)))

        if(NVAPI_Initialize.invokeInt(null) != 0) {
            logger.error("Failed to initialize NVAPI")
        }

        // initialise structs to correct version
        gpuUsages[0] = makeStructVersion(NVAPI_MAX_USAGES_PER_GPU * 4, 1)
        memoryInfo[0] = makeStructVersion(8 * 4, 3)

        NVAPI_EnumPhysicalGPUs.invokeInt(arrayOf(gpuHandles, gpuCount))

        utilisations["GPU"] = 0f
        utilisations["Framebuffer"] = 0f
        utilisations["Video Engine"] = 0f
        utilisations["Bus"] = 0f

        utilisations["TotalVideoMemory"] = 0f
        utilisations["AvailableVideoMemory"] = 0f
        utilisations["SystemVideoMemory"] = 0f
        utilisations["SharedSystemMemory"] = 0f
        utilisations["AvailableDedicatedVideoMemory"] = 0f
    }

    private fun makeStructVersion(sizeOfStruct: Int, version: Int): Int {
        return sizeOfStruct.or(version.shl(16))
    }

    override fun update(gpuIndex: Int) {
        if(failedTimesUsage <= 3) {
            val result = NVAPI_GPUGetUsages.invokeInt(arrayOf(gpuHandles[gpuIndex], gpuUsages))

            if (result == 0) {
                utilisations["GPU"] = gpuUsages[3].toFloat() / 100.0f
                utilisations["Framebuffer"] = gpuUsages[4].toFloat() / 100.0f
                utilisations["Video Engine"] = gpuUsages[5].toFloat() / 100.0f
                utilisations["Bus"] = gpuUsages[6].toFloat() / 100.0f
                failedTimesUsage = 0
            } else {
                failedTimesUsage++
                logger.warn("Failed to get GPU usage for $gpuIndex ($result)")
            }
        }

        if(failedTimesMemory <= 3) {
            val result = NVAPI_GetMemoryInfo.invokeInt(arrayOf(gpuHandles[gpuIndex], memoryInfo))

            if (result == 0) {
                utilisations["TotalVideoMemory"] = memoryInfo[1].toFloat() / 1024.0f
                utilisations["AvailableVideoMemory"] = memoryInfo[2].toFloat() / 1024.0f
                utilisations["SystemVideoMemory"] = memoryInfo[3].toFloat() / 1024.0f
                utilisations["SharedSystemMemory"] = memoryInfo[4].toFloat() / 1024.0f
                utilisations["AvailableDedicatedVideoMemory"] = memoryInfo[5].toFloat() / 1024.0f
                failedTimesMemory = 0
            } else {
                failedTimesMemory++
                logger.warn("Failed to get GPU memory usage for $gpuIndex ($result)")
            }
        }
    }

    override fun get(name: String): Float {
        return utilisations.getOrDefault(name, 0.0f)
    }

    override fun utilisationToString(): String {
        return "GPU ${utilisations["GPU"]}, Bus ${utilisations["Bus"]}, Video Engine ${utilisations["Video Engine"]}, Framebuffer ${utilisations["Framebuffer"]}"
    }

    override fun memoryUtilisationToString(): String {
        return "Memory ${utilisations["AvailableDedicatedVideoMemory"]}/${utilisations["TotalVideoMemory"]}"
    }
}
