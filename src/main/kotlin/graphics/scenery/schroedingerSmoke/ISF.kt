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
    val iix: Array<Array<Array<Double>>>
    val iiy: Array<Array<Array<Double>>>
    val iiz: Array<Array<Array<Double>>>
    lateinit var SchroedingerMask: Array<Array<Array<Complex>>>

    init {
        iix = Array(nx) { i -> Array(ny) { j -> Array(nz) { k -> i.toDouble() } } }
        iiy = Array(nx) { i -> Array(ny) { j -> Array(nz) { k -> j.toDouble() } } }
        iiz = Array(nx) { i -> Array(ny) { j -> Array(nz) { k -> k.toDouble() } } }
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
}
