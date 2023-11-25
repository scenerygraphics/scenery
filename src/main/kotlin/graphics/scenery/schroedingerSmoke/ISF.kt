package graphics.scenery.schroedingerSmoke

import kotlin.math.pow
import kotlin.math.PI as pi


class ISF(val hBar: Float, val dt: Float, val torusDEC: TorusDEC) {
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

    fun pressureProject(psi1: Array<Array<Array<Complex>>>, psi2: Array<Array<Array<Complex>>>): Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
        val (vx, vy, vz) = velocityOneForm(psi1, psi2)
        val div = torusDEC.div(vx, vy, vz)
        val q = torusDEC.poissonSolve(div)
        return gaugeTransform(psi1, psi2, q)
    }

    fun velocityOneForm(psi1: Array<Array<Array<Complex>>>, psi2: Array<Array<Array<Complex>>>, hbar: Double = 1.0): Triple<Array<Array<Array<Double>>>, Array<Array<Array<Double>>>, Array<Array<Array<Double>>>> {
        val ixp = Array(nx) { (it + 1) % nx }
        val iyp = Array(ny) { (it + 1) % ny }
        val izp = Array(nz) { (it + 1) % nz }

        val vx = Array(nx) { i ->
            Array(ny) { j ->
                Array(nz) { k ->
                    angle(psi1[i][j][k].conjugate().times(psi1[ixp[i]][j][k].plus(psi2[i][j][k].conjugate().times(psi2[ixp[i]][j][k]).times(Complex(hbar, 0.0)))))
                }
            }
        }

        val vy = Array(nx) { i ->
            Array(ny) { j ->
                Array(nz) { k ->
                    angle(psi1[i][j][k].conjugate().times(psi1[i][iyp[j]][k].plus(psi2[i][j][k].conjugate().times(psi2[i][iyp[j]][k]).times(Complex(hbar, 0.0)))))
                }
            }
        }

        val vz = Array(nx) { i ->
            Array(ny) { j ->
                Array(nz) { k ->
                    angle(psi1[i][j][k].conjugate().times(psi1[i][j][izp[k]].plus(psi2[i][j][k].conjugate().times(psi2[i][j][izp[k]]).times(Complex(hbar, 0.0)))))
                }
            }
        }

        return Triple(vx, vy, vz)
    }

    private fun angle(c: Complex): Double {
        // Implement the angle (phase) calculation for a complex number
        return Math.atan2(c.im, c.re)
    }
}
