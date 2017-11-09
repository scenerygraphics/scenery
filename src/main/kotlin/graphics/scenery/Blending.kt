package graphics.scenery

/**
 * Class providing different blend modes, transparency and blend factor settings
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
data class Blending(
    var transparent: Boolean = false,
    var opacity: Float = 1.0f,

    var colorBlending: BlendOp = BlendOp.add,
    var alphaBlending: BlendOp = BlendOp.add,
    var sourceColorBlendFactor: BlendFactor = BlendFactor.One,
    var destinationColorBlendFactor: BlendFactor = BlendFactor.One,
    var sourceAlphaBlendFactor: BlendFactor = BlendFactor.One,
    var destinationAlphaBlendFactor: BlendFactor = BlendFactor.One
) {
    enum class BlendOp { add, subtract, reverse_subtract, min, max }

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
}

