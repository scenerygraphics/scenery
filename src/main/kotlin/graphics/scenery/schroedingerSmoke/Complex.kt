package graphics.scenery.schroedingerSmoke

data class Complex(val re: Double, val im: Double) {
    fun plus(other: Complex) = Complex(re + other.re, im + other.im)
    fun minus(other: Complex) = Complex(re - other.re, im - other.im)
    fun times(other: Complex) = Complex(re * other.re - im * other.im, re * other.im + im * other.re)
    fun scale(factor: Double) = Complex(re * factor, im * factor)

    companion object {
        fun cos(angle: Double) = Complex(kotlin.math.cos(angle), 0.0)
        fun sin(angle: Double) = Complex(kotlin.math.sin(angle), 0.0)
    }
}

fun complexProd(a: Complex, b: Complex): Complex {
    return a.times(b)
}

