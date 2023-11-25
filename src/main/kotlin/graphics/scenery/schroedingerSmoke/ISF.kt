package graphics.scenery.schroedingerSmoke

import kotlin.math.pow
import kotlin.math.PI as pi


class ISF(val hBar: Float, val dt: Float, torusDEC: TorusDEC) {
    val nx = torusDEC.resx
    val ny = torusDEC.resy
    val nz = torusDEC.resz
    val sizex = torusDEC.sizex
    val sizey = torusDEC.sizey
    val sizez = torusDEC.sizez
    val iix: Array<Array<Array<Double>>> = Array(nx) { i -> Array(ny) { j -> Array(nz) { k -> i.toDouble() } } }
    val iiy: Array<Array<Array<Double>>> = Array(nx) { i -> Array(ny) { j -> Array(nz) { k -> j.toDouble() } } }
    val iiz: Array<Array<Array<Double>>> = Array(nx) { i -> Array(ny) { j -> Array(nz) { k -> k.toDouble() } } }
    lateinit var SchroedingerMask: Array<Array<Array<Complex>>>

    init {
        buildSchroedinger()
    }

    fun buildSchroedinger() {
        val fac = -4 * Math.PI * Math.PI * hBar

        SchroedingerMask = Array(nx) { i ->
            Array(ny) { j ->
                Array(nz) { k ->
                    val kx = (iix[i][j][k] - 1 - nx / 2) / sizex
                    val ky = (iiy[i][j][k] - 1 - ny / 2) / sizey
                    val kz = (iiz[i][j][k] - 1 - nz / 2) / sizez

                    val lambda = fac * (kx.pow(2) + ky.pow(2) + kz.pow(2))
                    expCom(Complex(0.0, lambda * dt / 2))
                }
            }
        }
    }
    fun expCom(c: Complex): Complex {
        // Implement the complex exponential function
        // This is a placeholder implementation
        return Complex(Math.exp(c.re) * Math.cos(c.im), Math.exp(c.re) * Math.sin(c.im))
    }

    fun schroedingerFlow(psi1: Array<Array<Array<Complex>>>, psi2: Array<Array<Array<Complex>>>): Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
        var psi1Fft = fftn(psi1)
        var psi2Fft = fftn(psi2)

        psi1Fft = elementWiseMultiply(psi1Fft, SchroedingerMask)
        psi2Fft = elementWiseMultiply(psi2Fft, SchroedingerMask)

        val psi1Result = ifftn(psi1Fft)
        val psi2Result = ifftn(psi2Fft)

        return Pair(psi1Result, psi2Result)
    }

    private fun fftn(data: Array<Array<Array<Complex>>>): Array<Array<Array<Complex>>> {
        // Implement FFT or use a library function
        return data // Placeholder
    }

    private fun ifftn(data: Array<Array<Array<Complex>>>): Array<Array<Array<Complex>>> {
        // Implement IFFT or use a library function
        return data // Placeholder
    }

    private fun elementWiseMultiply(a: Array<Array<Array<Complex>>>, b: Array<Array<Array<Complex>>>): Array<Array<Array<Complex>>> {
        return Array(a.size) { i ->
            Array(a[0].size) { j ->
                Array(a[0][0].size) { k ->
                    complexProd(a[i][j][k], b[i][j][k]) // Assuming Complex class has an operator overload for multiplication
                }
            }
        }
    }


}
