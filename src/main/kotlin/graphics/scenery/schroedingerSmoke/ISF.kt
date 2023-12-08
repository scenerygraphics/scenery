package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.*

class ISF(
    override val sizex: Int,
    override val sizey: Int,
    override val sizez: Int,
    override val resx: Int,
    override val resy: Int,
    override val resz: Int
) : TorusDEC(sizex, sizey, sizez, resx, resy, resz) {

    var hbar = 0.1
    var dt = 1.0 / 24
    lateinit var schroedingerMask: Array<Array<Array<Complex>>>

    private val transformer = FastFourierTransformer(DftNormalization.STANDARD)

    fun buildSchroedinger() {
        val fac = -4 * PI.pow(2) * hbar
        schroedingerMask = Array(resx) { iix ->
            Array(resy) { iiy ->
                Array(resz) { iiz ->
                    val kx = (iix - resx / 2).toDouble() / sizex
                    val ky = (iiy - resy / 2).toDouble() / sizey
                    val kz = (iiz - resz / 2).toDouble() / sizez
                    val lambda = fac * (kx.pow(2) + ky.pow(2) + kz.pow(2))
                    Complex(cos(lambda * dt / 2), sin(lambda * dt / 2))
                }
            }
        }
    }

    fun schroedingerFlow(psi1: Array<Array<DoubleArray>>, psi2: Array<Array<DoubleArray>>): Pair<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val psi1Complex = fft(psi1)
        val psi2Complex = fft(psi2)

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    psi1Complex[i][j][k] = psi1Complex[i][j][k].multiply(schroedingerMask[i][j][k])
                    psi2Complex[i][j][k] = psi2Complex[i][j][k].multiply(schroedingerMask[i][j][k])
                }
            }
        }

        return Pair(ifft(psi1Complex), ifft(psi2Complex))
    }

    // Implementing FFT using Apache Commons Math
    private fun fft(data: Array<Array<DoubleArray>>): Array<Array<Array<Complex>>> {
        val complexData = data.map { plane ->
            plane.map { row ->
                row.map { value -> Complex(value, 0.0) }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()

        return complexData.map { plane ->
            plane.map { row ->
                transformer.transform(row, TransformType.FORWARD)
            }.toTypedArray()
        }.toTypedArray()
    }

    // Implementing inverse FFT using Apache Commons Math
    private fun ifft(data: Array<Array<Array<Complex>>>): Array<Array<DoubleArray>> {
        return data.map { plane ->
            plane.map { row ->
                transformer.transform(row, TransformType.INVERSE).map { it.real }.toDoubleArray()
            }.toTypedArray()
        }.toTypedArray()
    }

    fun pressureProject(psi1: Array<Array<DoubleArray>>, psi2: Array<Array<DoubleArray>>): Pair<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val (vx, vy, vz) = velocityOneForm(psi1, psi2)
        val div = div(vx, vy, vz)
        val q = poissonSolve(div)
        return gaugeTransform(psi1, psi2, q)
    }

    private fun velocityOneForm(psi1: Array<Array<DoubleArray>>, psi2: Array<Array<DoubleArray>>, hbar: Double = 1.0): Triple<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val vx = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val vy = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val vz = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    val ixp = (i + 1) % resx
                    val iyp = (j + 1) % resy
                    val izp = (k + 1) % resz
                    vx[i][j][k] = hbar * atan2(psi1[ixp][j][k] * psi2[i][j][k] - psi1[i][j][k] * psi2[ixp][j][k], psi1[ixp][j][k] * psi1[i][j][k] + psi2[ixp][j][k] * psi2[i][j][k])
                    vy[i][j][k] = hbar * atan2(psi1[i][iyp][k] * psi2[i][j][k] - psi1[i][j][k] * psi2[i][iyp][k], psi1[i][iyp][k] * psi1[i][j][k] + psi2[i][iyp][k] * psi2[i][j][k])
                    vz[i][j][k] = hbar * atan2(psi1[i][j][izp] * psi2[i][j][k] - psi1[i][j][k] * psi2[i][j][izp], psi1[i][j][izp] * psi1[i][j][k] + psi2[i][j][izp] * psi2[i][j][k])
                }
            }
        }

        return Triple(vx, vy, vz)
    }

    companion object {
        fun gaugeTransform(psi1: Array<Array<DoubleArray>>, psi2: Array<Array<DoubleArray>>, q: Array<Array<DoubleArray>>): Pair<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
            val transformedPsi1 = psi1.mapIndexed { i, plane ->
                plane.mapIndexed { j, row ->
                    row.mapIndexed { k, value ->
                        value * exp(1.0 * q[i][j][k]).pow(2)
                    }.toDoubleArray()
                }.toTypedArray()
            }.toTypedArray()

            val transformedPsi2 = psi2.mapIndexed { i, plane ->
                plane.mapIndexed { j, row ->
                    row.mapIndexed { k, value ->
                        value * exp(1.0 * q[i][j][k]).pow(2)
                    }.toDoubleArray()
                }.toTypedArray()
            }.toTypedArray()

            return Pair(transformedPsi1, transformedPsi2)
        }

        fun normalize(psi1: Array<Array<DoubleArray>>, psi2: Array<Array<DoubleArray>>): Pair<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
            val psiNorm = psi1.zip(psi2).map { (plane1, plane2) ->
                plane1.zip(plane2).map { (row1, row2) ->
                    row1.zip(row2).map { (value1, value2) ->
                        sqrt(value1.pow(2) + value2.pow(2))
                    }
                }
            }

            val normalizedPsi1 = psi1.mapIndexed { i, plane ->
                plane.mapIndexed { j, row ->
                    row.mapIndexed { k, value ->
                        value / psiNorm[i][j][k]
                    }.toDoubleArray()
                }.toTypedArray()
            }.toTypedArray()

            val normalizedPsi2 = psi2.mapIndexed { i, plane ->
                plane.mapIndexed { j, row ->
                    row.mapIndexed { k, value ->
                        value / psiNorm[i][j][k]
                    }.toDoubleArray()
                }.toTypedArray()
            }.toTypedArray()

            return Pair(normalizedPsi1, normalizedPsi2)
        }
    }
}
