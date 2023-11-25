package graphics.scenery.schroedingerSmoke

import kotlin.math.pow
import kotlin.math.sqrt


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

    fun addCircle(psi: Array<Array<Array<Complex>>>, center: Triple<Double, Double, Double>, normal: Triple<Double, Double, Double>, r: Double, d: Double): Array<Array<Array<Complex>>> {
        val (centerX, centerY, centerZ) = center
        val (normX, normY, normZ) = normalize(normal)

        val rx = torusDEC.px.map { it - centerX }
        val ry = torusDEC.py.map { it - centerY }
        val rz = torusDEC.pz.map { it - centerZ }

        val alpha = Array(rx.size) { i ->
            Array(ry.size) { j ->
                Array(rz.size) { k ->
                    val z = rx[i] * normX + ry[j] * normY + rz[k] * normZ
                    val inCylinder = rx[i].pow(2) + ry[j].pow(2) + rz[k].pow(2) - z.pow(2) < r.pow(2)
                    val inLayerP = z > 0 && z <= d / 2 && inCylinder
                    val inLayerM = z <= 0 && z >= -d / 2 && inCylinder

                    when {
                        inLayerP -> -Math.PI * (2 * z / d - 1)
                        inLayerM -> -Math.PI * (2 * z / d + 1)
                        else -> 0.0
                    }
                }
            }
        }

        return psi.mapIndexed { i, psiX ->
            psiX.mapIndexed { j, psiY ->
                psiY.mapIndexed { k, psiZ ->
                    psiZ.times(expCom(Complex(0.0, alpha[i][j][k])))
                }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()
    }

    private fun normalize(v: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        val (x, y, z) = v
        val norm = Math.sqrt(x.pow(2) + y.pow(2) + z.pow(2))
        return Triple(x / norm, y / norm, z / norm)
    }

    companion object {
        fun gaugeTransform(
            psi1: Array<Array<Array<Complex>>>,
            psi2: Array<Array<Array<Complex>>>,
            q: Array<Array<DoubleArray>>
        ): Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
            val eiq =
                q.map { layer -> layer.map { row -> row.map { expCom(Complex(0.0, it)) }.toTypedArray() }.toTypedArray() }

            return Pair(
                psi1.elementWiseMultiply(eiq.toTypedArray()),
                psi2.elementWiseMultiply(eiq.toTypedArray())
            )
        }

        fun hopf(
            psi1: Array<Array<Array<Complex>>>,
            psi2: Array<Array<Array<Complex>>>
        ): Triple<Array<Array<Array<Double>>>, Array<Array<Array<Double>>>, Array<Array<Array<Double>>>> {
            val sx = psi1.combineWith(psi2) { a, c -> 2 * (a.re * c.re + a.im * c.im) }
            val sy = psi1.combineWith(psi2) { a, c -> 2 * (a.re * c.im - a.im * c.re) }
            val sz = psi1.combineWith(psi2) { a, c -> a.re.pow(2) + a.im.pow(2) - c.re.pow(2) - c.im.pow(2) }

            return Triple(sx, sy, sz)
        }

        fun normalize(
            psi1: Array<Array<Array<Complex>>>,
            psi2: Array<Array<Array<Complex>>>
        ): Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
            val psiNorm = psi1.combineWith(psi2) { a, b -> sqrt(a.absSquared() + b.absSquared()) }

            return Pair(
                psi1.elementWiseDivide(psiNorm),
                psi2.elementWiseDivide(psiNorm)
            )
        }

        private fun Array<Array<Array<Complex>>>.elementWiseMultiply(other: Array<Array<Array<Complex>>>): Array<Array<Array<Complex>>> {
            // Implement element-wise multiplication
            return other
        }

        private fun Array<Array<Array<Complex>>>.elementWiseDivide(other: Array<Array<Array<Double>>>): Array<Array<Array<Complex>>> {
            // Implement element-wise division
            return this
        }

        private fun Array<Array<Array<Complex>>>.combineWith(other: Array<Array<Array<Complex>>>, operation: (Complex, Complex) -> Double): Array<Array<Array<Double>>> {
            // Implement combination with a custom operation
            return arrayOf(arrayOf(arrayOf(0.0)))
        }
        fun expCom(c: Complex): Complex {
            // Implement the complex exponential function
            // This is a placeholder implementation
            return Complex(Math.exp(c.re) * Math.cos(c.im), Math.exp(c.re) * Math.sin(c.im))
        }
    }
}
