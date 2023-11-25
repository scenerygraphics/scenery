package graphics.scenery.schroedingerSmoke

import kotlin.math.pow

data class Complex(val re: Double, val im: Double) {
    fun plus(other: Complex) = Complex(re + other.re, im + other.im)
    fun minus(other: Complex) = Complex(re - other.re, im - other.im)
    fun times(other: Complex) = Complex(re * other.re - im * other.im, re * other.im + im * other.re)
    fun scale(factor: Double) = Complex(re * factor, im * factor)
    fun conjugate() = Complex(re, -im)
    fun absSquared() = re.pow(2) + im.pow(2)

    companion object {
        fun cos(angle: Double) = Complex(kotlin.math.cos(angle), 0.0)
        fun sin(angle: Double) = Complex(kotlin.math.sin(angle), 0.0)
    }
}

fun complexProd(a: Complex, b: Complex): Complex {
    return a.times(b)
}

