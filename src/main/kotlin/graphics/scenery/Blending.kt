package graphics.scenery


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

    // TODO: Implemnent residual blend factors
    enum class BlendFactor { Zero, One, OneMinusSrcAlpha, SrcAlpha }
}

