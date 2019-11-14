package graphics.scenery

/**
 * Class providing different blend modes, transparency and blend factor settings.
 * Blending options are modeled after the standard OpenGL and Vulkan blending modes.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
data class Blending(
    /** Turns on and off transparency. */
    var transparent: Boolean = false,
    /** How transparent the object should be. */
    var opacity: Float = 1.0f,

    /** Color blending operation, see [BlendOp]. */
    var colorBlending: BlendOp = BlendOp.add,
    /** Alpha blending operation, see [BlendOp]. */
    var alphaBlending: BlendOp = BlendOp.add,
    /** Source color blend factor. */
    var sourceColorBlendFactor: BlendFactor = BlendFactor.One,
    /** Destination color blend factor. */
    var destinationColorBlendFactor: BlendFactor = BlendFactor.Zero,
    /** Source Alpha blend factor. */
    var sourceAlphaBlendFactor: BlendFactor = BlendFactor.One,
    /** Destination Alpha blend factor. */
    var destinationAlphaBlendFactor: BlendFactor = BlendFactor.Zero
) {
    /** Blending operations. */
    enum class BlendOp { add, subtract, reverse_subtract, min, max }

    /** Blend factors. */
    enum class BlendFactor {
        Zero,
        One,

        SrcColor,
        OneMinusSrcColor,

        DstColor,
        OneMinusDstColor,

        SrcAlpha,
        OneMinusSrcAlpha,

        DstAlpha,
        OneMinusDstAlpha,

        ConstantColor,
        OneMinusConstantColor,
        ConstantAlpha,
        OneMinusConstantAlpha,

        Src1Color,
        OneMinusSrc1Color,

        Src1Alpha,
        OneMinusSrc1Alpha,

        SrcAlphaSaturate
    }

    /**
     * Sets Photoshop overlay-like blending options.
     */
    fun setOverlayBlending() {
        sourceColorBlendFactor = Blending.BlendFactor.SrcAlpha
        destinationColorBlendFactor = Blending.BlendFactor.OneMinusSrcAlpha
        sourceAlphaBlendFactor = Blending.BlendFactor.One
        destinationAlphaBlendFactor = Blending.BlendFactor.Zero
        colorBlending = Blending.BlendOp.add
        alphaBlending = Blending.BlendOp.add
    }
}

