package graphics.scenery.backends.dlss

import com.sun.jna.*
import com.sun.jna.Function
import com.sun.jna.Function.C_CONVENTION
import com.sun.jna.ptr.*
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import graphics.scenery.Hub
import graphics.scenery.Hubable
import graphics.scenery.backends.dlss.DLSS.Companion.logger
import graphics.scenery.backends.vulkan.toHexString
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*

interface DLSSWindowsLibrary: StdCallLibrary {
    companion object {
        val instance = Native.loadLibrary("nvngx_dlss.dll", DLSSWindowsLibrary::class.java)
    }


    fun NVSDK_NGX_VULKAN_GetFeatureInstanceExtensionRequirements(
        FeatureDiscoveryInfo: NVSDK_NGX_FeatureDiscoveryInfo,
        InOutExtensionCount: IntByReference,
        OutExtensionProperties: PointerByReference
    ): Int

    fun NVSDK_NGX_VULKAN_GetFeatureDeviceExtensionRequirements(
        Instance: Long,
        PhysicalDevice: Long,
        FeatureDiscoveryInfo: NVSDK_NGX_FeatureDiscoveryInfo,
        InOutExtensionCount: IntByReference,
        OutExtensionProperties: PointerByReference
    ): Int

}

interface NGXWindowsLibrary: StdCallLibrary {
    companion object {
        val instance = Native.loadLibrary("nvngx.dll", NGXWindowsLibrary::class.java)
    }

    fun NVSDK_NGX_VULKAN_Init_with_ProjectID(
        InProjectId: String,
        InEngineType: Int,
        InEngineVersion: String,
        InApplicationDataPath: WString,
        InInstance: Long,
        InPD: Long,
        InDevice: Long,
        InGIPA: Long?,
        InGDPA: Long?,
        InFeatureInfo: NGXFeatureCommonInfo?,
        InSDKVersion: Int
    ): Int

    fun NVSDK_NGX_VULKAN_GetCapabilityParameters(
        parameters: PointerByReference
    ): Int

    fun NVSDK_NGX_VULKAN_CreateFeature1(
        Device: Long,
        CommandBuffer: Long,
        Features: Int,
        Parameters: Pointer,
        OutHandle: PointerByReference
    ): Int

    fun GetNGXResultAsString(InNGXResult: Int): Long

    fun NVSDK_NGX_Parameter_GetI(params: Pointer, name: String, p: IntByReference): Int
    fun NVSDK_NGX_Parameter_GetF(params: Pointer, name: String, p: FloatByReference): Int
    fun NVSDK_NGX_Parameter_GetD(params: Pointer, name: String, p: DoubleByReference): Int
    fun NVSDK_NGX_Parameter_GetUI(params: Pointer, name: String, p: IntByReference): Int
    fun NVSDK_NGX_Parameter_GetULL(params: Pointer, name: String, p: LongByReference): Int
    fun NVSDK_NGX_Parameter_GetVoidPointer(params: Pointer, name: String, p: PointerByReference): Int

    fun NVSDK_NGX_Parameter_SetI(params: Pointer, name: String, value: Int): Int
    fun NVSDK_NGX_Parameter_SetUI(params: Pointer, name: String, value: Int): Int
}

enum class NGXLoggingLevel {
    Off,
    On,
    Verbose,
    Num
}

interface AppLogCallback: StdCallCallback {
    fun callback(message: String, loggingLevel: Int, feature: Int)
}

class LoggingInfo(): Structure() {
    @JvmField var LoggingCallback: AppLogCallback = object: AppLogCallback {
        override fun callback(message: String, loggingLevel: Int, feature: Int) {
            logger.info("$message, level=$loggingLevel, feature=$feature")
        }
    }
    @JvmField var minimumLoggingLevel: Int = NGXLoggingLevel.Verbose.ordinal
    @JvmField var disableOtherLoggingSinks: Boolean = false

    constructor(callback: AppLogCallback) : this() {
        LoggingCallback = callback
    }

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("LoggingCallback", "minimumLoggingLevel", "disableOtherLoggingSinks")
}

class NGXPathListInfo : Structure() {
    @JvmField var Path: Pointer = Pointer(0)
    @JvmField var Length: Int = 1

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("Path", "Length")
}

enum class NVSDK_NGX_DLSS_Feature_Flags(value: Int)
{
    NVSDK_NGX_DLSS_Feature_Flags_IsInvalid(1 shl 31),

    NVSDK_NGX_DLSS_Feature_Flags_None(0),
    NVSDK_NGX_DLSS_Feature_Flags_IsHDR(1 shl 0),
    NVSDK_NGX_DLSS_Feature_Flags_MVLowRes(1 shl 1),
    NVSDK_NGX_DLSS_Feature_Flags_MVJittered(1 shl 2),
    NVSDK_NGX_DLSS_Feature_Flags_DepthInverted(1 shl 3),
    NVSDK_NGX_DLSS_Feature_Flags_Reserved_0(1 shl 4),
    NVSDK_NGX_DLSS_Feature_Flags_DoSharpening(1 shl 5),
    NVSDK_NGX_DLSS_Feature_Flags_AutoExposure(1 shl 6),
}

class FeatureCreateParams : Structure() {
    @JvmField var InWidth: Int = 0
    @JvmField var InHeight: Int = 0
    @JvmField var InTargetWidth: Int = 0
    @JvmField var InTargetHeight: Int = 0
    @JvmField var InPerfQualityValue: Int = 0

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("InWidth", "InHeight", "InTargetWidth", "InTargetHeight", "InPerfQualityValue")
}

class DLSSCreateParams: Structure() {
    @JvmField var Feature: FeatureCreateParams = FeatureCreateParams()
    @JvmField var InFeatureCreateFlags: Int = 0
    @JvmField var InEnableOutputSubrects: Boolean = false

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("InFeatureCreateFlags", "InEnableOutputSubrects")
}

/*
typedef struct NVSDK_NGX_VK_Feature_Eval_Params
{
    NVSDK_NGX_Resource_VK *pInColor;
    NVSDK_NGX_Resource_VK *pInOutput;
    /*** OPTIONAL for DLSS ***/
    float                  InSharpness;
} NVSDK_NGX_VK_Feature_Eval_Params;
 */

/*
typedef struct NVSDK_NGX_VK_DLSS_Eval_Params
{
    NVSDK_NGX_VK_Feature_Eval_Params    Feature;
    NVSDK_NGX_Resource_VK *             pInDepth;
    NVSDK_NGX_Resource_VK *             pInMotionVectors;
    float                               InJitterOffsetX;     /* Jitter offset must be in input/render pixel space */
    float                               InJitterOffsetY;
    NVSDK_NGX_Dimensions                InRenderSubrectDimensions;
    /*** OPTIONAL - leave to 0/0.0f if unused ***/
    int                                 InReset;             /* Set to 1 when scene changes completely (new level etc) */
    float                               InMVScaleX;          /* If MVs need custom scaling to convert to pixel space */
    float                               InMVScaleY;
    NVSDK_NGX_Resource_VK *             pInTransparencyMask; /* Unused/Reserved for future use */
    NVSDK_NGX_Resource_VK *             pInExposureTexture;
    NVSDK_NGX_Resource_VK *             pInBiasCurrentColorMask;
    NVSDK_NGX_Coordinates               InColorSubrectBase;
    NVSDK_NGX_Coordinates               InDepthSubrectBase;
    NVSDK_NGX_Coordinates               InMVSubrectBase;
    NVSDK_NGX_Coordinates               InTranslucencySubrectBase;
    NVSDK_NGX_Coordinates               InBiasCurrentColorSubrectBase;
    NVSDK_NGX_Coordinates               InOutputSubrectBase;
    float                               InPreExposure;
    float                               InExposureScale;
    int                                 InIndicatorInvertXAxis;
    int                                 InIndicatorInvertYAxis;
    /*** OPTIONAL - only for research purposes ***/
    NVSDK_NGX_VK_GBuffer                GBufferSurface;
    NVSDK_NGX_ToneMapperType            InToneMapperType;
    NVSDK_NGX_Resource_VK *             pInMotionVectors3D;
    NVSDK_NGX_Resource_VK *             pInIsParticleMask; /* to identify which pixels contains particles, essentially that are not drawn as part of base pass */
    NVSDK_NGX_Resource_VK *             pInAnimatedTextureMask; /* a binary mask covering pixels occupied by animated textures */
    NVSDK_NGX_Resource_VK *             pInDepthHighRes;
    NVSDK_NGX_Resource_VK *             pInPositionViewSpace;
    float                               InFrameTimeDeltaInMsec; /* helps in determining the amount to denoise or anti-alias based on the speed of the object from motion vector magnitudes and fps as determined by this delta */
    NVSDK_NGX_Resource_VK *             pInRayTracingHitDistance; /* for each effect - approximation to the amount of noise in a ray-traced color */
    NVSDK_NGX_Resource_VK *             pInMotionVectorsReflections; /* motion vectors of reflected objects like for mirrored surfaces */
} NVSDK_NGX_VK_DLSS_Eval_Params;
 */

class NGXImageViewInfo: Structure() {
    @JvmField var ImageView: Long = 0L
    @JvmField var Image: Long = 0L
    @JvmField var ImageSubresourceRange: Long = 0L
    @JvmField var Format: Long = 0L
    @JvmField var Width: Int = 0
    @JvmField var Height: Int = 0


    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("ImageView", "Image", "ImageSubresourceRange", "Format", "Width", "Height")
}
class NGXVulkanResource: Structure() {
    @JvmField var ImageViewInfo: NGXImageViewInfo = NGXImageViewInfo()
    // NVSDK_NGX_RESOURCE_VK_TYPE_VK_IMAGEVIEW
    @JvmField var Type: Int = 0
    @JvmField var ReadWrite: Boolean = false
    
    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("ImageViewInfo", "Type", "ReadWrite")
}

class DLSSFeatureEvalParams: Structure() {
    @JvmField var pInColor: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInOutput: NGXVulkanResource = NGXVulkanResource()
    @JvmField var InSharpness = 1.0f

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("pInColor", "pInOutput", "InSharpness")
}

class NGXDimensions: Structure() {
    @JvmField var Width: Int = 0
    @JvmField var Height: Int = 0
    
    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("Width", "Height")
}

class NGXCoordinates: Structure() {
    @JvmField var X: Int = 0
    @JvmField var Y: Int = 0

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("X", "Y")
}

class NGXGBuffer: Structure() {
    @JvmField var pInAttrib: Array<NGXVulkanResource> = arrayOf()
}
class DLSSEvaluationParams: Structure() {
    @JvmField var Feature: DLSSFeatureEvalParams = DLSSFeatureEvalParams()
    @JvmField var pInDepth: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInMotionVectors: NGXVulkanResource = NGXVulkanResource()
    @JvmField var InJitterOffsetX = 1.0f
    @JvmField var InJitterOffsetY = 1.0f
    @JvmField var InRenderSubrectDimensions: NGXDimensions = NGXDimensions()
    @JvmField var InReset: Int = 0
    @JvmField var InMVScaleX: Float = 1.0f
    @JvmField var InMVScaleY: Float = 1.0f
    @JvmField var pInTransparencyMask: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInExposureTexture: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInBiasCurrentColorMask: NGXVulkanResource = NGXVulkanResource()
    @JvmField var InColorSubrectBase = NGXCoordinates()
    @JvmField var InDepthSubrectBase = NGXCoordinates()
    @JvmField var InMVSubrectBase = NGXCoordinates()
    @JvmField var InTranslucencySubrectBase = NGXCoordinates()
    @JvmField var InBiasCurrentColorSubrectBase = NGXCoordinates()
    @JvmField var InOutputSubrectBase = NGXCoordinates()
    @JvmField var InPreExposure: Float = 1.0f
    @JvmField var InExposureScale: Float = 1.0f
    @JvmField var InIndicatorInvertXAxis: Int = 0
    @JvmField var InIndicatorInvertYAxis: Int = 0
    // those are optional and for research purposes
    @JvmField var GBufferSurface: NGXGBuffer = NGXGBuffer()
    @JvmField var InToneMapperType: Int = 0
    @JvmField var pInMotionVectors3D: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInIsParticleMask: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInAnimatedTextureMask: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInDepthHighRes: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInPositionViewSpace: NGXVulkanResource = NGXVulkanResource()
    @JvmField var InFrameTimeDeltaInMsec: Float = 0.0f
    @JvmField var pInRayTracingHitDistance: NGXVulkanResource = NGXVulkanResource()
    @JvmField var pInMotionVectorsReflections: NGXVulkanResource = NGXVulkanResource()
}

open class NGXFeatureCommonInfo: Structure() {
    @JvmField var PathListInfo: NGXPathListInfo = NGXPathListInfo()
    @JvmField var InternalInfo: Long = 0
    @JvmField var LoggingInfo: LoggingInfo = LoggingInfo(
        object: AppLogCallback {
            override fun callback(message: String, loggingLevel: Int, feature: Int) {
                logger.info("$message, level=$loggingLevel, feature=$feature")
            }
        }
    )

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("PathListInfo", "InternalInfo", "LoggingInfo")

    class ByReference : NGXFeatureCommonInfo(), Structure.ByReference
}

class NVSDK_NGX_ProjectIdDescription: Structure() {
    @JvmField var ProjectId: String = "459d6734-62a6-4d47-927a-bedcdb0445c5"
    // NVSDK_NGX_ENGINE_TYPE_CUSTOM
    @JvmField var EngineType: Int = 0
    @JvmField var EngineVersion: String = "1.0.237"

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("ProjectId", "EngineType", "EngineVersion")
}

class v: Structure() {
    @JvmField var ProjectDesc: NVSDK_NGX_ProjectIdDescription = NVSDK_NGX_ProjectIdDescription()

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("ProjectDesc")
}

class NVSDK_NGX_ApplicationIdentifier: Structure() {
    @JvmField var IdentifierType: Int = 0
    @JvmField var v: v = v()

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("IdentifierType", "v")
}

class NVSDK_NGX_FeatureDiscoveryInfo: Structure() {
    @JvmField var SDKVersion: Int = 0x15
    @JvmField var FeatureID: Int = 1
    @JvmField var ApplicationIdentifier: NVSDK_NGX_ApplicationIdentifier = NVSDK_NGX_ApplicationIdentifier()
    @JvmField var ApplicationDataPath: String = "."
    @JvmField var FeatureInfo = NGXFeatureCommonInfo()

    override fun getFieldOrder(): MutableList<String> =
        mutableListOf("SDKVersion", "FeatureID", "ApplicationIdentifier", "ApplicationDataPath", "FeatureInfo")
}

class DLSS(override var hub: Hub?) : AutoCloseable, Hubable {
    private val logger by LazyLogger()

    init {
        NVSDK_NGX_ProjectIdDescription().size()
        v().size()
        NVSDK_NGX_ApplicationIdentifier().size()
        NVSDK_NGX_FeatureDiscoveryInfo().size()

    }

    fun getRequiredInstanceExtensions(): VkExtensionProperties.Buffer {
        val featureDiscoveryInfo = NVSDK_NGX_FeatureDiscoveryInfo()

        val count = IntByReference()
        val ref = PointerByReference()

        val error = DLSSWindowsLibrary.instance.NVSDK_NGX_VULKAN_GetFeatureInstanceExtensionRequirements(
            featureDiscoveryInfo,
            count,
            ref
        )

        logger.info("NVSDK_NGX_VULKAN_GetFeatureInstanceExtensionRequirements: $error/${NGXWindowsLibrary.instance.GetNGXResultAsString(error)}")

        val ext = VkExtensionProperties.Buffer(Pointer.nativeValue(ref.value), count.value)
        logger.info("${count.value} required Vulkan instance extensions returned (${Pointer.nativeValue(ref.value).toHexString()}):")
        (0 until count.value).forEach {
            logger.info("${ext.get(it).extensionNameString()}")
        }
        return ext
    }


    fun getRequiredDeviceExtensions(instance: VkInstance, physicalDevice: VkPhysicalDevice): VkExtensionProperties.Buffer {
        val featureDiscoveryInfo = NVSDK_NGX_FeatureDiscoveryInfo()

        val count = IntByReference()
        val ref = PointerByReference()

        val error = DLSSWindowsLibrary.instance.NVSDK_NGX_VULKAN_GetFeatureDeviceExtensionRequirements(
            instance.address(),
            physicalDevice.address(),
            featureDiscoveryInfo,
            count,
            ref
        )

        logger.info("NVSDK_NGX_VULKAN_GetFeatureDeviceExtensionRequirements: $error/${NGXWindowsLibrary.instance.GetNGXResultAsString(error)}")

        val ext = VkExtensionProperties.Buffer(Pointer.nativeValue(ref.value), count.value)
        logger.info("${count.value} required Vulkan device extensions returned (${Pointer.nativeValue(ref.value).toHexString()}):")
        (0 until count.value).forEach {
            logger.info("${ext.get(it).extensionNameString()}")
        }
        return ext
    }

    fun init(applicationId: Long, vkInstance: Long, physicalDevice: Long, device: Long) {
        val error = NGXWindowsLibrary.instance.NVSDK_NGX_VULKAN_Init_with_ProjectID(
            "959d6734-62a6-4d47-927a-bedcdb0445c5",
            0,
            "1337",
            WString("."),
            vkInstance,
            physicalDevice,
            device,
            null,
            null,
            NGXFeatureCommonInfo(),
            0x15
        )
        if(error == 1) {
            logger.info("DLSS initialised successfully.")
        } else {
            logger.warn("DLSS returned $error/${getNGXResultAsString(error)}")
        }
    }

    fun getNGXResultAsString(error: Int): String {
        val address = NGXWindowsLibrary.instance.GetNGXResultAsString(error)
        return MemoryUtil.memUTF16(address)
    }

    lateinit var p: NGXParameters
    fun getDLSSSupported(): Boolean {
        val params = PointerByReference()
        val result = NGXWindowsLibrary.instance.NVSDK_NGX_VULKAN_GetCapabilityParameters(params)
        if(result != 1) {
            logger.error("Unable to get capability parameters from NGX")
            return false
        }

        p = NGXParameters(NGXWindowsLibrary.instance, params)
        val needsDriverUpdate = p.getInt(p.NVSDK_NGX_Parameter_SuperSampling_NeedsUpdatedDriver)
        val minDriverVersion = p.getInt(p.NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMajor) to p.getInt(p.NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMinor)
        if(needsDriverUpdate > 0) {
            logger.warn("Graphics driver needs to be updated to support DLSS. Minimum required version: $needsDriverUpdate / ${minDriverVersion.first}.${minDriverVersion.second}")
            return false
        }

        val dlssAvailable = p.getUInt(p.NVSDK_NGX_Parameter_SuperSampling_Available)
        val dlssAvailable2 = p.getInt(p.NVSDK_NGX_Parameter_SuperSampling_FeatureInitResult)

        if(dlssAvailable == 0U || dlssAvailable2 == 0) {
            logger.error("DLSS is not supported on this hardware (available: ${dlssAvailable} featureInitResult: ${dlssAvailable2}).")
            return false
        }

        return true
    }

    data class OptimalDLSSSettings(
        val windowWidth: Int,
        val windowHeight: Int,
        val dynamicMaximumRenderSizeWidth: Int,
        val dynamicMaximumRenderSizeHeight: Int,
        val dynamicMinimumRenderSizeHeight: Int,
        val dynamicMinimumRenderSizeWidth: Int,
        val sharpness: Float,
        val preset: DLSSPreset
    )

    enum class DLSSPreset {
        MaxPerformance,
        Balance,
        MaxQuality,
        UltraPerformance,
        UltraQuality
    }
    fun getOptimalSettings(
        forPreset: DLSSPreset,
        targetWidth: Int,
        targetHeight: Int
    ): OptimalDLSSSettings {
        val callback = p.getVoidPointer(p.NVSDK_NGX_Parameter_DLSSOptimalSettingsCallback)
            ?: throw IllegalStateException("Coult not determine optimal settings callback. This is likely due to DLSS being unsupported.")

        p.set(p.NVSDK_NGX_Parameter_Width, targetWidth.toUInt())
        p.set(p.NVSDK_NGX_Parameter_Height, targetHeight.toUInt())
        p.set(p.NVSDK_NGX_Parameter_PerfQualityValue, forPreset.ordinal)
        p.set(p.NVSDK_NGX_Parameter_RTXValue, 0)

        val f = Function.getFunction(callback, C_CONVENTION, "UTF-8")
        val r = f.invoke(Int::class.java, arrayOf(p.params.value)) as Int

        if(r != 1) {
            throw IllegalStateException("Failed to get optimal settings, ${getNGXResultAsString(r)} ($r)")
        }

        return OptimalDLSSSettings(
            p.getUInt(p.NVSDK_NGX_Parameter_OutWidth).toInt(),
            p.getUInt(p.NVSDK_NGX_Parameter_OutHeight).toInt(),
            p.getUInt(p.NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Max_Render_Width).toInt(),
            p.getUInt(p.NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Max_Render_Height).toInt(),
            p.getUInt(p.NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Min_Render_Width).toInt(),
            p.getUInt(p.NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Min_Render_Height).toInt(),
            p.getFloat(p.NVSDK_NGX_Parameter_Sharpness),
            forPreset
        )
    }

    fun createFeature(device: VkDevice, commandBuffer: VkCommandBuffer, creationNodeMask: Int = 0, visibilityNodeMask: Int = 0, optimalSettings: OptimalDLSSSettings? = null): Pointer {
        val createParams = DLSSCreateParams()
        val featureHandle = PointerByReference()

        if(optimalSettings != null) {
            createParams.Feature.InWidth = optimalSettings.windowWidth
            createParams.Feature.InHeight = optimalSettings.windowHeight

            createParams.Feature.InTargetWidth = optimalSettings.dynamicMaximumRenderSizeWidth
            createParams.Feature.InTargetHeight = optimalSettings.dynamicMaximumRenderSizeHeight
            createParams.Feature.InPerfQualityValue = optimalSettings.preset.ordinal
        }

        p.set(p.NVSDK_NGX_Parameter_CreationNodeMask, creationNodeMask)
        p.set(p.NVSDK_NGX_Parameter_VisibilityNodeMask, visibilityNodeMask)

        p.set(p.NVSDK_NGX_Parameter_Width, createParams.Feature.InWidth)
        p.set(p.NVSDK_NGX_Parameter_Height, createParams.Feature.InHeight)

        p.set(p.NVSDK_NGX_Parameter_OutWidth, createParams.Feature.InTargetWidth)
        p.set(p.NVSDK_NGX_Parameter_OutHeight, createParams.Feature.InTargetHeight)

        p.set(p.NVSDK_NGX_Parameter_PerfQualityValue, createParams.Feature.InPerfQualityValue)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Feature_Create_Flags, createParams.InFeatureCreateFlags)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Enable_Output_Subrects, if(createParams.InEnableOutputSubrects) 1 else 0)

        NGXWindowsLibrary.instance.NVSDK_NGX_VULKAN_CreateFeature1(
            device.address(),
            commandBuffer.address(),
            // NVSDK_NGX_Feature_SuperSampling
            1,
            p.params.value,
            featureHandle
        )

        return featureHandle.value
    }

    fun evaluateFeature(feature: Pointer, commandBuffer: VkCommandBuffer, evalParams: DLSSEvaluationParams) {
        p.set(p.NVSDK_NGX_Parameter_Color, evalParams.Feature.pInColor)
        p.set(p.NVSDK_NGX_Parameter_Output, evalParams.Feature.pInOutput)
        p.set(p.NVSDK_NGX_Parameter_Depth, evalParams.pInDepth)
        p.set(p.NVSDK_NGX_Parameter_MotionVectors, evalParams.pInMotionVectors)
        p.set(p.NVSDK_NGX_Parameter_Jitter_Offset_X, evalParams.InJitterOffsetX)
        p.set(p.NVSDK_NGX_Parameter_Jitter_Offset_Y, evalParams.InJitterOffsetY)
        p.set(p.NVSDK_NGX_Parameter_Sharpness, evalParams.Feature.InSharpness)
        p.set(p.NVSDK_NGX_Parameter_Reset, evalParams.InReset)
        p.set(p.NVSDK_NGX_Parameter_MV_Scale_X, evalParams.InMVScaleX == 0.0f ? 1.0f : pInDlssEvalParams->InMVScaleX)
        p.set(p.NVSDK_NGX_Parameter_MV_Scale_Y, evalParams.InMVScaleY == 0.0f ? 1.0f : pInDlssEvalParams->InMVScaleY)
        p.set(p.NVSDK_NGX_Parameter_TransparencyMask, evalParams.pInTransparencyMask)
        p.set(p.NVSDK_NGX_Parameter_ExposureTexture, evalParams.pInExposureTexture)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Bias_Current_Color_Mask, evalParams.pInBiasCurrentColorMask)
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Albedo, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_ALBEDO])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Roughness, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_ROUGHNESS])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Metallic, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_METALLIC])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Specular, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_SPECULAR])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Subsurface, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_SUBSURFACE])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Normals, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_NORMALS])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_ShadingModelId, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_SHADINGMODELID])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_MaterialId, evalParams.GBufferSurface.pInAttrib[NVSDK_NGX_GBUFFER_MATERIALID])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_8, evalParams.GBufferSurface.pInAttrib[8])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_9, evalParams.GBufferSurface.pInAttrib[9])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_10, evalParams.GBufferSurface.pInAttrib[10])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_11, evalParams.GBufferSurface.pInAttrib[11])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_12, evalParams.GBufferSurface.pInAttrib[12])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_13, evalParams.GBufferSurface.pInAttrib[13])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_14, evalParams.GBufferSurface.pInAttrib[14])
        p.set(p.NVSDK_NGX_Parameter_GBuffer_Atrrib_15, evalParams.GBufferSurface.pInAttrib[15])
        p.set(p.NVSDK_NGX_Parameter_TonemapperType, evalParams.InToneMapperType)
        p.set(p.NVSDK_NGX_Parameter_MotionVectors3D, evalParams.pInMotionVectors3D)
        p.set(p.NVSDK_NGX_Parameter_IsParticleMask, evalParams.pInIsParticleMask)
        p.set(p.NVSDK_NGX_Parameter_AnimatedTextureMask, evalParams.pInAnimatedTextureMask)
        p.set(p.NVSDK_NGX_Parameter_DepthHighRes, evalParams.pInDepthHighRes)
        p.set(p.NVSDK_NGX_Parameter_Position_ViewSpace, evalParams.pInPositionViewSpace)
        p.set(p.NVSDK_NGX_Parameter_FrameTimeDeltaInMsec, evalParams.InFrameTimeDeltaInMsec)
        p.set(p.NVSDK_NGX_Parameter_RayTracingHitDistance, evalParams.pInRayTracingHitDistance)
        p.set(p.NVSDK_NGX_Parameter_MotionVectorsReflection, evalParams.pInMotionVectorsReflections)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Color_Subrect_Base_X, evalParams.InColorSubrectBase.X)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Color_Subrect_Base_Y, evalParams.InColorSubrectBase.Y)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Depth_Subrect_Base_X, evalParams.InDepthSubrectBase.X)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Depth_Subrect_Base_Y, evalParams.InDepthSubrectBase.Y)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_MV_SubrectBase_X, evalParams.InMVSubrectBase.X)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_MV_SubrectBase_Y, evalParams.InMVSubrectBase.Y)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Translucency_SubrectBase_X, evalParams.InTranslucencySubrectBase.X)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Translucency_SubrectBase_Y, evalParams.InTranslucencySubrectBase.Y)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Bias_Current_Color_SubrectBase_X, evalParams.InBiasCurrentColorSubrectBase.X)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Input_Bias_Current_Color_SubrectBase_Y, evalParams.InBiasCurrentColorSubrectBase.Y)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Output_Subrect_Base_X, evalParams.InOutputSubrectBase.X)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Output_Subrect_Base_Y, evalParams.InOutputSubrectBase.Y)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Render_Subrect_Dimensions_Width , evalParams.InRenderSubrectDimensions.Width)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Render_Subrect_Dimensions_Height, evalParams.InRenderSubrectDimensions.Height)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Pre_Exposure, evalParams.InPreExposure == 0.0f ? 1.0f : pInDlssEvalParams->InPreExposure)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Exposure_Scale, evalParams.InExposureScale == 0.0f ? 1.0f : pInDlssEvalParams->InExposureScale)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Indicator_Invert_X_Axis, evalParams.InIndicatorInvertXAxis)
        p.set(p.NVSDK_NGX_Parameter_DLSS_Indicator_Invert_Y_Axis, evalParams.InIndicatorInvertYAxis)
        
        NGXWindowsLibrary.instance.NVSDK_NGX_VULKAN_EvaluateFeature_C(commandBuffer.address(), feature, p.params.value, null)
    }

    override fun close() {

    }

    companion object {
        val logger by LazyLogger()
    }
}

