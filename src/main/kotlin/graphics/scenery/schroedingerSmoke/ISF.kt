package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import org.joml.Vector3f
import kotlin.math.*

class ISF(
    override val sizex: Int,
    override val sizey: Int,
    override val sizez: Int,
    override val resx: Int,
    override val resy: Int,
    override val resz: Int,
    val hBar: Double = 0.1,
    val dt: Double = 1/24.toDouble()
) : TorusDEC(sizex, sizey, sizez, resx, resy, resz) {

    override val dx = sizex.toDouble() / resx
    override val dy = sizey.toDouble() / resy
    override val dz = sizez.toDouble() / resz

    override val ix: IntArray = IntArray(resx) { it }
    override val iy: IntArray = IntArray(resy) { it }
    override val iz: IntArray = IntArray(resz) { it }

    override val px: Array<Array<DoubleArray>> = Array(resx) { iix -> Array(resy) { iiy -> DoubleArray(resz) { iiz -> iix * dx } } }
    override val py: Array<Array<DoubleArray>> = Array(resx) { Array(resy) { iiy -> DoubleArray(resz) { iiz -> iiy * dy } } }
    override val pz: Array<Array<DoubleArray>> = Array(resx) { Array(resy) { iiy -> DoubleArray(resz) { iiz -> iiz * dz } } }

    var schroedingerMask: Array<Array<Array<Complex>>>

    private val transformer = FastFourierTransformer(DftNormalization.STANDARD)

    init {
        val fac = -4 * PI.pow(2) * hBar
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

    fun schroedingerFlow(
        psi: Array<Array<Array<Complex>>>,
        fftTransformer: FastFourierTransformer = FastFourierTransformer(DftNormalization.STANDARD)
    ): Array<Array<Array<Complex>>> {

        val transformedPsi = Array(psi.size) { Array(psi[0].size) { Array(psi[0][0].size) { Complex.ZERO } } }

        for (i in psi.indices) {
            for (j in psi[i].indices) {
                val line = psi[i][j].copyOf()
                val transformedLine = fftTransformer.transform(line, TransformType.FORWARD)

                // Apply the Schroedinger mask and perform inverse FFT
                for (k in transformedLine.indices) {
                    transformedPsi[i][j][k] = transformedLine[k].multiply(schroedingerMask[i][j][k])
                }

                val inverseTransformedLine = fftTransformer.transform(transformedPsi[i][j], TransformType.INVERSE)
                for (k in inverseTransformedLine.indices) {
                    transformedPsi[i][j][k] = inverseTransformedLine[k]
                }
            }
        }

        return transformedPsi
    }


    fun pressureProject(psi: Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>>):
        Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
        val psi1 = psi.first
        val psi2 = psi.second
        val (vx, vy, vz) = velocityOneForm(psi1, psi2)
        val div = div(vx, vy, vz)
        val q = poissonSolve(div)
        return gaugeTransform(psi1, psi2, q)
    }

    fun velocityOneForm(
        psi1: Array<Array<Array<Complex>>>,
        psi2: Array<Array<Array<Complex>>>,
        hbar: Double = 1.0
    ): Triple<Array<Array<Array<Double>>>, Array<Array<Array<Double>>>, Array<Array<Array<Double>>>> {

        val vx = Array(psi1.size) { i ->
            Array(psi1[i].size) { j ->
                Array(psi1[i][j].size) { k ->
                    val ixp = (ix[i] + 1) % resx
                    val angle = (psi1[i][j][k].conjugate().multiply(psi1[ixp][j][k]).add(psi2[i][j][k].conjugate().multiply(psi2[ixp][j][k]))).argument
                    angle * hbar
                }
            }
        }

        val vy = Array(psi1.size) { i ->
            Array(psi1[i].size) { j ->
                Array(psi1[i][j].size) { k ->
                    val iyp = (iy[j] + 1) % resy
                    val angle = (psi1[i][j][k].conjugate().multiply(psi1[i][iyp][k]).add(psi2[i][j][k].conjugate().multiply(psi2[i][iyp][k]))).argument
                    angle * hbar
                }
            }
        }

        val vz = Array(psi1.size) { i ->
            Array(psi1[i].size) { j ->
                Array(psi1[i][j].size) { k ->
                    val izp = (iz[k] + 1) % resz
                    val angle = (psi1[i][j][k].conjugate().multiply(psi1[i][j][izp]).add(psi2[i][j][k].conjugate().multiply(psi2[i][j][izp]))).argument
                    angle * hbar
                }
            }
        }

        return Triple(vx, vy, vz)
    }

    fun addCircle(
        psi: Array<Array<Array<Complex>>>,
        center: Vector3f,
        normal: Vector3f,
        r: Double,
        d: Double,
        dx: Double, dy: Double, dz: Double  // Assuming grid spacings are provided
    ): Array<Array<Array<Complex>>> {
        // Normalize the normal vector
        val normalizedNormal = normal.normalize()

        // Iterate through each point in the psi array
        return psi.mapIndexed { i, layer ->
            layer.mapIndexed { j, row ->
                row.mapIndexed { k, value ->
                    // Calculate relative position to the center
                    val rx = i * dx - center.x
                    val ry = j * dy - center.y
                    val rz = k * dz - center.z

                    // Project relative position onto the normal vector
                    val z = Vector3f(rx.toFloat(), ry.toFloat(), rz.toFloat()).dot(normalizedNormal)

                    // Check if the point is within the cylinder and layer
                    val inCylinder = rx.pow(2) + ry.pow(2) + rz.pow(2) - z.pow(2) < r.pow(2)
                    val inLayerP = z > 0 && z <= d / 2 && inCylinder
                    val inLayerM = z <= 0 && z >= -d / 2 && inCylinder

                    // Calculate the phase shift
                    val alpha = when {
                        inLayerP -> -PI * (2 * z / d - 1)
                        inLayerM -> -PI * (2 * z / d + 1)
                        else -> 0.0
                    }

                    // Apply the phase shift to psi
                    value.multiply(Complex(0.0, alpha).exp())
                }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()
    }
    companion object {
        fun gaugeTransform(psi1: Array<Array<Array<Complex>>>, psi2: Array<Array<Array<Complex>>>, q: Array<Array<DoubleArray>>): Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
            val transformedPsi1 = psi1.indices.map { i ->
                psi1[i].indices.map { j ->
                    psi1[i][j].indices.map { k ->
                        val eiq = Complex(0.0, q[i][j][k]).exp()
                        psi1[i][j][k].multiply(eiq)
                    }.toTypedArray()
                }.toTypedArray()
            }.toTypedArray()

            val transformedPsi2 = psi2.indices.map { i ->
                psi2[i].indices.map { j ->
                    psi2[i][j].indices.map { k ->
                        val eiq = Complex(0.0, q[i][j][k]).exp()
                        psi2[i][j][k].multiply(eiq)
                    }.toTypedArray()
                }.toTypedArray()
            }.toTypedArray()

            return Pair(transformedPsi1, transformedPsi2)
        }

        fun normalize(psi: Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>>):
            Pair<Array<Array<Array<Complex>>>, Array<Array<Array<Complex>>>> {
            val psi1 = psi.first
            val psi2 = psi.second
            val psiNorm = Array(psi1.size) { i ->
                Array(psi1[i].size) { j ->
                    Array(psi1[i][j].size) { k ->
                        sqrt(psi1[i][j][k].abs().pow(2.0) +
                            psi2[i][j][k].abs().pow(2.0))
                    }
                }
            }

            val normalizedPsi1 = Array(psi1.size) { i ->
                Array(psi1[i].size) { j ->
                    Array(psi1[i][j].size) { k ->
                        psi1[i][j][k].divide(psiNorm[i][j][k])
                    }
                }
            }

            val normalizedPsi2 = Array(psi2.size) { i ->
                Array(psi2[i].size) { j ->
                    Array(psi2[i][j].size) { k ->
                        psi2[i][j][k].divide(psiNorm[i][j][k])
                    }
                }
            }

            return Pair(normalizedPsi1, normalizedPsi2)
        }

        // extract Clebsch variable s=(sx, sy, sz) from psi1, psi2
        fun hopf(psi1: Array<Array<Array<Complex>>>, psi2: Array<Array<Array<Complex>>>): Triple<Array<Array<Array<Double>>>, Array<Array<Array<Double>>>, Array<Array<Array<Double>>>> {
            val sx = Array(psi1.size) { i ->
                Array(psi1[i].size) { j ->
                    Array(psi1[i][j].size) { k ->
                        val a = psi1[i][j][k].real
                        val b = psi1[i][j][k].imaginary
                        val c = psi2[i][j][k].real
                        val d = psi2[i][j][k].imaginary
                        2 * (a * c + b * d)
                    }
                }
            }

            val sy = Array(psi1.size) { i ->
                Array(psi1[i].size) { j ->
                    Array(psi1[i][j].size) { k ->
                        val a = psi1[i][j][k].real
                        val b = psi1[i][j][k].imaginary
                        val c = psi2[i][j][k].real
                        val d = psi2[i][j][k].imaginary
                        2 * (a * d - b * c)
                    }
                }
            }

            val sz = Array(psi1.size) { i ->
                Array(psi1[i].size) { j ->
                    Array(psi1[i][j].size) { k ->
                        val a = psi1[i][j][k].real
                        val b = psi1[i][j][k].imaginary
                        val c = psi2[i][j][k].real
                        val d = psi2[i][j][k].imaginary
                        a * a + b * b - c * c - d * d
                    }
                }
            }

            return Triple(sx, sy, sz)
        }
    }
}
