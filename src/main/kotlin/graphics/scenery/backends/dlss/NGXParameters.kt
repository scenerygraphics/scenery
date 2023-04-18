package graphics.scenery.backends.dlss

import com.sun.jna.Pointer
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import graphics.scenery.utils.LazyLogger
import org.lwjgl.system.MemoryUtil

class NGXParameters(val instance: NGXWindowsLibrary, val params: PointerByReference) {
    private val logger by LazyLogger()

    val NVSDK_NGX_Parameter_OptLevel = "Snippet.OptLevel"
    val NVSDK_NGX_Parameter_IsDevSnippetBranch = "Snippet.IsDevBranch"
    val NVSDK_NGX_Parameter_SuperSampling_ScaleFactor = "SuperSampling.ScaleFactor"
    val NVSDK_NGX_Parameter_ImageSignalProcessing_ScaleFactor = "ImageSignalProcessing.ScaleFactor"
    val NVSDK_NGX_Parameter_SuperSampling_Available = "SuperSampling.Available"
    val NVSDK_NGX_Parameter_InPainting_Available = "InPainting.Available"
    val NVSDK_NGX_Parameter_ImageSuperResolution_Available = "ImageSuperResolution.Available"
    val NVSDK_NGX_Parameter_SlowMotion_Available = "SlowMotion.Available"
    val NVSDK_NGX_Parameter_VideoSuperResolution_Available = "VideoSuperResolution.Available"
    val NVSDK_NGX_Parameter_ImageSignalProcessing_Available = "ImageSignalProcessing.Available"
    val NVSDK_NGX_Parameter_DeepResolve_Available = "DeepResolve.Available"
    val NVSDK_NGX_Parameter_DeepDVC_Available = "DeepDVC.Available"
    val NVSDK_NGX_Parameter_SuperSampling_NeedsUpdatedDriver = "SuperSampling.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_InPainting_NeedsUpdatedDriver = "InPainting.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_ImageSuperResolution_NeedsUpdatedDriver = "ImageSuperResolution.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_SlowMotion_NeedsUpdatedDriver = "SlowMotion.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_VideoSuperResolution_NeedsUpdatedDriver = "VideoSuperResolution.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_ImageSignalProcessing_NeedsUpdatedDriver = "ImageSignalProcessing.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_DeepResolve_NeedsUpdatedDriver = "DeepResolve.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_DeepDVC_NeedsUpdatedDriver = "DeepDVC.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_FrameInterpolation_NeedsUpdatedDriver = "FrameInterpolation.NeedsUpdatedDriver"
    val NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMajor = "SuperSampling.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_InPainting_MinDriverVersionMajor = "InPainting.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_ImageSuperResolution_MinDriverVersionMajor =
        "ImageSuperResolution.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_SlowMotion_MinDriverVersionMajor = "SlowMotion.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_VideoSuperResolution_MinDriverVersionMajor =
        "VideoSuperResolution.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_ImageSignalProcessing_MinDriverVersionMajor =
        "ImageSignalProcessing.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_DeepResolve_MinDriverVersionMajor = "DeepResolve.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_DeepDVC_MinDriverVersionMajor = "DeepDVC.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_FrameInterpolation_MinDriverVersionMajor = "FrameInterpolation.MinDriverVersionMajor"
    val NVSDK_NGX_Parameter_SuperSampling_MinDriverVersionMinor = "SuperSampling.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_InPainting_MinDriverVersionMinor = "InPainting.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_ImageSuperResolution_MinDriverVersionMinor =
        "ImageSuperResolution.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_SlowMotion_MinDriverVersionMinor = "SlowMotion.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_VideoSuperResolution_MinDriverVersionMinor =
        "VideoSuperResolution.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_ImageSignalProcessing_MinDriverVersionMinor =
        "ImageSignalProcessing.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_DeepResolve_MinDriverVersionMinor = "DeepResolve.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_DeepDVC_MinDriverVersionMinor = "DeepDVC.MinDriverVersionMinor"
    val NVSDK_NGX_Parameter_SuperSampling_FeatureInitResult = "SuperSampling.FeatureInitResult"
    val NVSDK_NGX_Parameter_InPainting_FeatureInitResult = "InPainting.FeatureInitResult"
    val NVSDK_NGX_Parameter_ImageSuperResolution_FeatureInitResult = "ImageSuperResolution.FeatureInitResult"
    val NVSDK_NGX_Parameter_SlowMotion_FeatureInitResult = "SlowMotion.FeatureInitResult"
    val NVSDK_NGX_Parameter_VideoSuperResolution_FeatureInitResult = "VideoSuperResolution.FeatureInitResult"
    val NVSDK_NGX_Parameter_ImageSignalProcessing_FeatureInitResult = "ImageSignalProcessing.FeatureInitResult"
    val NVSDK_NGX_Parameter_DeepResolve_FeatureInitResult = "DeepResolve.FeatureInitResult"
    val NVSDK_NGX_Parameter_DeepDVC_FeatureInitResult = "DeepDVC.FeatureInitResult"
    val NVSDK_NGX_Parameter_FrameInterpolation_FeatureInitResult = "FrameInterpolation.FeatureInitResult"
    val NVSDK_NGX_Parameter_ImageSuperResolution_ScaleFactor_2_1 = "ImageSuperResolution.ScaleFactor.2.1"
    val NVSDK_NGX_Parameter_ImageSuperResolution_ScaleFactor_3_1 = "ImageSuperResolution.ScaleFactor.3.1"
    val NVSDK_NGX_Parameter_ImageSuperResolution_ScaleFactor_3_2 = "ImageSuperResolution.ScaleFactor.3.2"
    val NVSDK_NGX_Parameter_ImageSuperResolution_ScaleFactor_4_3 = "ImageSuperResolution.ScaleFactor.4.3"
    val NVSDK_NGX_Parameter_DeepDVC_Strength = "DeepDVC.Strength"
    val NVSDK_NGX_Parameter_NumFrames = "NumFrames"
    val NVSDK_NGX_Parameter_Scale = "Scale"
    val NVSDK_NGX_Parameter_Width = "Width"
    val NVSDK_NGX_Parameter_Height = "Height"
    val NVSDK_NGX_Parameter_OutWidth = "OutWidth"
    val NVSDK_NGX_Parameter_OutHeight = "OutHeight"
    val NVSDK_NGX_Parameter_Sharpness = "Sharpness"
    val NVSDK_NGX_Parameter_Scratch = "Scratch"
    val NVSDK_NGX_Parameter_Scratch_SizeInBytes = "Scratch.SizeInBytes"
    val NVSDK_NGX_Parameter_Input1 = "Input1"
    val NVSDK_NGX_Parameter_Input1_Format = "Input1.Format"
    val NVSDK_NGX_Parameter_Input1_SizeInBytes = "Input1.SizeInBytes"
    val NVSDK_NGX_Parameter_Input2 = "Input2"
    val NVSDK_NGX_Parameter_Input2_Format = "Input2.Format"
    val NVSDK_NGX_Parameter_Input2_SizeInBytes = "Input2.SizeInBytes"
    val NVSDK_NGX_Parameter_Color = "Color"
    val NVSDK_NGX_Parameter_Color_Format = "Color.Format"
    val NVSDK_NGX_Parameter_Color_SizeInBytes = "Color.SizeInBytes"
    val NVSDK_NGX_Parameter_FI_Color1 = "Color1"
    val NVSDK_NGX_Parameter_FI_Color2 = "Color2"
    val NVSDK_NGX_Parameter_Albedo = "Albedo"
    val NVSDK_NGX_Parameter_Output = "Output"
    val NVSDK_NGX_Parameter_Output_SizeInBytes = "Output.SizeInBytes"
    val NVSDK_NGX_Parameter_FI_Output1 = "Output1"
    val NVSDK_NGX_Parameter_FI_Output2 = "Output2"
    val NVSDK_NGX_Parameter_FI_Output3 = "Output3"
    val NVSDK_NGX_Parameter_Reset = "Reset"
    val NVSDK_NGX_Parameter_BlendFactor = "BlendFactor"
    val NVSDK_NGX_Parameter_MotionVectors = "MotionVectors"
    val NVSDK_NGX_Parameter_FI_MotionVectors1 = "MotionVectors1"
    val NVSDK_NGX_Parameter_FI_MotionVectors2 = "MotionVectors2"
    val NVSDK_NGX_Parameter_Rect_X = "Rect.X"
    val NVSDK_NGX_Parameter_Rect_Y = "Rect.Y"
    val NVSDK_NGX_Parameter_Rect_W = "Rect.W"
    val NVSDK_NGX_Parameter_Rect_H = "Rect.H"
    val NVSDK_NGX_Parameter_MV_Scale_X = "MV.Scale.X"
    val NVSDK_NGX_Parameter_MV_Scale_Y = "MV.Scale.Y"
    val NVSDK_NGX_Parameter_Model = "Model"
    val NVSDK_NGX_Parameter_Format = "Format"
    val NVSDK_NGX_Parameter_SizeInBytes = "SizeInBytes"
    val NVSDK_NGX_Parameter_ResourceAllocCallback = "ResourceAllocCallback"
    val NVSDK_NGX_Parameter_BufferAllocCallback = "BufferAllocCallback"
    val NVSDK_NGX_Parameter_Tex2DAllocCallback = "Tex2DAllocCallback"
    val NVSDK_NGX_Parameter_ResourceReleaseCallback = "ResourceReleaseCallback"
    val NVSDK_NGX_Parameter_CreationNodeMask = "CreationNodeMask"
    val NVSDK_NGX_Parameter_VisibilityNodeMask = "VisibilityNodeMask"
    val NVSDK_NGX_Parameter_MV_Offset_X = "MV.Offset.X"
    val NVSDK_NGX_Parameter_MV_Offset_Y = "MV.Offset.Y"
    val NVSDK_NGX_Parameter_Hint_UseFireflySwatter = "Hint.UseFireflySwatter"
    val NVSDK_NGX_Parameter_Resource_Width = "ResourceWidth"
    val NVSDK_NGX_Parameter_Resource_Height = "ResourceHeight"
    val NVSDK_NGX_Parameter_Resource_OutWidth = "ResourceOutWidth"
    val NVSDK_NGX_Parameter_Resource_OutHeight = "ResourceOutHeight"
    val NVSDK_NGX_Parameter_Depth = "Depth"
    val NVSDK_NGX_Parameter_FI_Depth1 = "Depth1"
    val NVSDK_NGX_Parameter_FI_Depth2 = "Depth2"
    val NVSDK_NGX_Parameter_DLSSOptimalSettingsCallback = "DLSSOptimalSettingsCallback"
    val NVSDK_NGX_Parameter_DLSSGetStatsCallback = "DLSSGetStatsCallback"
    val NVSDK_NGX_Parameter_PerfQualityValue = "PerfQualityValue"
    val NVSDK_NGX_Parameter_RTXValue = "RTXValue"
    val NVSDK_NGX_Parameter_DLSSMode = "DLSSMode"
    val NVSDK_NGX_Parameter_FI_Mode = "FIMode"
    val NVSDK_NGX_Parameter_FI_OF_Preset = "FIOFPreset"
    val NVSDK_NGX_Parameter_FI_OF_GridSize = "FIOFGridSize"
    val NVSDK_NGX_Parameter_Jitter_Offset_X = "Jitter.Offset.X"
    val NVSDK_NGX_Parameter_Jitter_Offset_Y = "Jitter.Offset.Y"
    val NVSDK_NGX_Parameter_Denoise = "Denoise"
    val NVSDK_NGX_Parameter_TransparencyMask = "TransparencyMask"
    val NVSDK_NGX_Parameter_ExposureTexture =
        "ExposureTexture" // a 1x1 texture containing the final exposure scale
    val NVSDK_NGX_Parameter_DLSS_Feature_Create_Flags = "DLSS.Feature.Create.Flags"
    val NVSDK_NGX_Parameter_DLSS_Checkerboard_Jitter_Hack = "DLSS.Checkerboard.Jitter.Hack"
    val NVSDK_NGX_Parameter_GBuffer_Normals = "GBuffer.Normals"
    val NVSDK_NGX_Parameter_GBuffer_Albedo = "GBuffer.Albedo"
    val NVSDK_NGX_Parameter_GBuffer_Roughness = "GBuffer.Roughness"
    val NVSDK_NGX_Parameter_GBuffer_DiffuseAlbedo = "GBuffer.DiffuseAlbedo"
    val NVSDK_NGX_Parameter_GBuffer_SpecularAlbedo = "GBuffer.SpecularAlbedo"
    val NVSDK_NGX_Parameter_GBuffer_IndirectAlbedo = "GBuffer.IndirectAlbedo"
    val NVSDK_NGX_Parameter_GBuffer_SpecularMvec = "GBuffer.SpecularMvec"
    val NVSDK_NGX_Parameter_GBuffer_DisocclusionMask = "GBuffer.DisocclusionMask"
    val NVSDK_NGX_Parameter_GBuffer_Metallic = "GBuffer.Metallic"
    val NVSDK_NGX_Parameter_GBuffer_Specular = "GBuffer.Specular"
    val NVSDK_NGX_Parameter_GBuffer_Subsurface = "GBuffer.Subsurface"
    val NVSDK_NGX_Parameter_GBuffer_ShadingModelId = "GBuffer.ShadingModelId"
    val NVSDK_NGX_Parameter_GBuffer_MaterialId = "GBuffer.MaterialId"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_8 = "GBuffer.Attrib.8"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_9 = "GBuffer.Attrib.9"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_10 = "GBuffer.Attrib.10"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_11 = "GBuffer.Attrib.11"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_12 = "GBuffer.Attrib.12"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_13 = "GBuffer.Attrib.13"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_14 = "GBuffer.Attrib.14"
    val NVSDK_NGX_Parameter_GBuffer_Atrrib_15 = "GBuffer.Attrib.15"
    val NVSDK_NGX_Parameter_TonemapperType = "TonemapperType"
    val NVSDK_NGX_Parameter_FreeMemOnReleaseFeature = "FreeMemOnReleaseFeature"
    val NVSDK_NGX_Parameter_MotionVectors3D = "MotionVectors3D"
    val NVSDK_NGX_Parameter_IsParticleMask = "IsParticleMask"
    val NVSDK_NGX_Parameter_AnimatedTextureMask = "AnimatedTextureMask"
    val NVSDK_NGX_Parameter_DepthHighRes = "DepthHighRes"
    val NVSDK_NGX_Parameter_Position_ViewSpace = "Position.ViewSpace"
    val NVSDK_NGX_Parameter_FrameTimeDeltaInMsec = "FrameTimeDeltaInMsec"
    val NVSDK_NGX_Parameter_RayTracingHitDistance = "RayTracingHitDistance"
    val NVSDK_NGX_Parameter_MotionVectorsReflection = "MotionVectorsReflection"
    val NVSDK_NGX_Parameter_DLSS_Enable_Output_Subrects = "DLSS.Enable.Output.Subrects"
    val NVSDK_NGX_Parameter_DLSS_Input_Color_Subrect_Base_X = "DLSS.Input.Color.Subrect.Base.X"
    val NVSDK_NGX_Parameter_DLSS_Input_Color_Subrect_Base_Y = "DLSS.Input.Color.Subrect.Base.Y"
    val NVSDK_NGX_Parameter_DLSS_Input_Depth_Subrect_Base_X = "DLSS.Input.Depth.Subrect.Base.X"
    val NVSDK_NGX_Parameter_DLSS_Input_Depth_Subrect_Base_Y = "DLSS.Input.Depth.Subrect.Base.Y"
    val NVSDK_NGX_Parameter_DLSS_Input_MV_SubrectBase_X = "DLSS.Input.MV.Subrect.Base.X"
    val NVSDK_NGX_Parameter_DLSS_Input_MV_SubrectBase_Y = "DLSS.Input.MV.Subrect.Base.Y"
    val NVSDK_NGX_Parameter_DLSS_Input_Translucency_SubrectBase_X = "DLSS.Input.Translucency.Subrect.Base.X"
    val NVSDK_NGX_Parameter_DLSS_Input_Translucency_SubrectBase_Y = "DLSS.Input.Translucency.Subrect.Base.Y"
    val NVSDK_NGX_Parameter_DLSS_Output_Subrect_Base_X = "DLSS.Output.Subrect.Base.X"
    val NVSDK_NGX_Parameter_DLSS_Output_Subrect_Base_Y = "DLSS.Output.Subrect.Base.Y"
    val NVSDK_NGX_Parameter_DLSS_Render_Subrect_Dimensions_Width = "DLSS.Render.Subrect.Dimensions.Width"
    val NVSDK_NGX_Parameter_DLSS_Render_Subrect_Dimensions_Height = "DLSS.Render.Subrect.Dimensions.Height"
    val NVSDK_NGX_Parameter_DLSS_Pre_Exposure = "DLSS.Pre.Exposure"
    val NVSDK_NGX_Parameter_DLSS_Exposure_Scale = "DLSS.Exposure.Scale"
    val NVSDK_NGX_Parameter_DLSS_Input_Bias_Current_Color_Mask = "DLSS.Input.Bias.Current.Color.Mask"
    val NVSDK_NGX_Parameter_DLSS_Input_Bias_Current_Color_SubrectBase_X =
        "DLSS.Input.Bias.Current.Color.Subrect.Base.X"
    val NVSDK_NGX_Parameter_DLSS_Input_Bias_Current_Color_SubrectBase_Y =
        "DLSS.Input.Bias.Current.Color.Subrect.Base.Y"
    val NVSDK_NGX_Parameter_DLSS_Indicator_Invert_Y_Axis = "DLSS.Indicator.Invert.Y.Axis"
    val NVSDK_NGX_Parameter_DLSS_Indicator_Invert_X_Axis = "DLSS.Indicator.Invert.X.Axis"
    val NVSDK_NGX_Parameter_DLSS_INV_VIEW_PROJECTION_MATRIX = "InvViewProjectionMatrix"
    val NVSDK_NGX_Parameter_DLSS_PROJECTION_MATRIX = "ProjectionMatrix"
    val NVSDK_NGX_Parameter_DLSS_CLIP_TO_PREV_CLIP_MATRIX = "ClipToPrevClipMatrix"

    val NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Max_Render_Width = "DLSS.Get.Dynamic.Max.Render.Width"
    val NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Max_Render_Height = "DLSS.Get.Dynamic.Max.Render.Height"
    val NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Min_Render_Width = "DLSS.Get.Dynamic.Min.Render.Width"
    val NVSDK_NGX_Parameter_DLSS_Get_Dynamic_Min_Render_Height = "DLSS.Get.Dynamic.Min.Render.Height"

    val NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_DLAA = "DLSS.Hint.Render.Preset.DLAA"
    val NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_Quality = "DLSS.Hint.Render.Preset.Quality"
    val NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_Balanced = "DLSS.Hint.Render.Preset.Balanced"
    val NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_Performance = "DLSS.Hint.Render.Preset.Performance"
    val NVSDK_NGX_Parameter_DLSS_Hint_Render_Preset_UltraPerformance = "DLSS.Hint.Render.Preset.UltraPerformance"

    fun set(name: String, value: Int) {
        val r = instance.NVSDK_NGX_Parameter_SetI(params.value, name, value)
        if(r != 1) {
            logger.error("Call to SetI($name, $value) failed, ${getNGXResultAsString(r)} ($r)")
        }
    }

    fun set(name: String, value: UInt) {
        val r = instance.NVSDK_NGX_Parameter_SetUI(params.value, name, value.toInt())
        if(r != 1) {
            logger.error("Call to SetUI($name, $value) failed, ${getNGXResultAsString(r)} ($r)")
        }
    }
    fun set(name: String, value: Long) {

    }

    fun getInt(name: String): Int {
        val p = IntByReference()
        val r = instance.NVSDK_NGX_Parameter_GetI(params.value, name, p)
        return if(r != 1) {
            logger.warn("Call to getInt($name) failed, ${getNGXResultAsString(r)} ($r)")
            -1
        } else {
            p.value
        }
    }

    fun getUInt(name: String): UInt {
        val p = IntByReference()
        val r = instance.NVSDK_NGX_Parameter_GetUI(params.value, name, p)
        return if(r != 1) {
            logger.error("Call to getUInt($name) failed, ${getNGXResultAsString(r)} ($r)")
            UInt.MAX_VALUE
        } else {
            p.value.toUInt()
        }
    }

    fun getLong(name: String): Long {
        val p = LongByReference()
        val r = instance.NVSDK_NGX_Parameter_GetULL(params.value, name, p)
        return if(r != 1) {
            logger.error("Call to getLong($name) failed, ${getNGXResultAsString(r)} ($r)")
            -1L
        } else {
            p.value
        }
    }

    fun getFloat(name: String): Float {
        val p = FloatByReference()
        val r = instance.NVSDK_NGX_Parameter_GetF(params.value, name, p)
        return if(r != 1) {
            logger.error("Call to getFloat($name) failed, ${getNGXResultAsString(r)} ($r)")
            Float.NEGATIVE_INFINITY
        } else {
            p.value
        }
    }

    fun getVoidPointer(name: String): Pointer? {
        val p = PointerByReference()
        val r = instance.NVSDK_NGX_Parameter_GetVoidPointer(params.value, name, p)
        return if(r != 1) {
            logger.error("Call to getVoidPointer($name) failed, ${getNGXResultAsString(r)} ($r)")
            Pointer.NULL
        } else {
            p.value
        }
    }

    fun getNGXResultAsString(error: Int): String {
        val address = instance.GetNGXResultAsString(error)
        return MemoryUtil.memUTF16(address)
    }
}
