package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.pow
import kotlin.math.sin


/**
    TorusDEC is a handle class that models an instance of a 3D grid with periodic
    boundaries in x,y,z direction, i.e. a 3-torus. DEC stands for "Discrete
    Exterior Calculus", a set of operations including exterior derivatives,
    codifferentials.

    Derived from the supplementary material to:
    Albert Chern, Felix Knöppel, Ulrich Pinkall, Peter Schröder, and Steffen Weißmann. 2016.
    Schrödinger's smoke. ACM Trans. Graph. 35, 4, Article 77 (July 2016), 13 pages.
    https://doi.org/10.1145/2897824.2925868

 */

open class TorusDEC(
    open val sizex: Int = 0,
    open val sizey: Int = 0,
    open val sizez: Int = 0,
    open val resx: Int = 1,
    open val resy: Int = 1,
    open val resz: Int = 1
) {

    val dx = sizex.toDouble() / resx
    val dy = sizey.toDouble() / resy
    val dz = sizez.toDouble() / resz

    val ix: IntArray = IntArray(resx) { it }
    val iy: IntArray = IntArray(resy) { it }
    val iz: IntArray = IntArray(resz) { it }

    val px: Array<Array<DoubleArray>> = Array(resx) { iix -> Array(resy) { iiy -> DoubleArray(resz) { iiz -> iix * dx } } }
    val py: Array<Array<DoubleArray>> = Array(resx) { Array(resy) { iiy -> DoubleArray(resz) { iiz -> iiy * dy } } }
    val pz: Array<Array<DoubleArray>> = Array(resx) { Array(resy) { iiy -> DoubleArray(resz) { iiz -> iiz * dz } } }

    fun poissonSolve(f: Array<Array<DoubleArray>>): Array<Array<DoubleArray>> {
        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val fftData = f.map { plane ->
            plane.map { row ->
                row.map { value -> Complex(value, 0.0) }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()

        // Apply FFT
        val transformed = fftData.map { plane ->
            plane.map { row ->
                transformer.transform(row, TransformType.FORWARD)
            }.toTypedArray()
        }.toTypedArray()

        // Compute sin values and denominators
        val sx = Array(resx) { iix -> sin(Math.PI * iix / resx) / dx }
        val sy = Array(resy) { iiy -> sin(Math.PI * iiy / resy) / dy }
        val sz = Array(resz) { iiz -> sin(Math.PI * iiz / resz) / dz }

        // Apply the spectral method operation
        for (iix in 0 until resx) {
            for (iiy in 0 until resy) {
                for (iiz in 0 until resz) {
                    val denom = sx[iix].pow(2) + sy[iiy].pow(2) + sz[iiz].pow(2)
                    val fac = if (iix == 0 && iiy == 0 && iiz == 0) Complex.ZERO else Complex(-0.25 / denom, 0.0)
                    transformed[iix][iiy][iiz] = transformed[iix][iiy][iiz].multiply(fac)
                }
            }
        }

        // Apply inverse FFT
        val result = transformed.map { plane ->
            plane.map { row ->
                transformer.transform(row, TransformType.INVERSE)
            }.toTypedArray()
        }.toTypedArray()

        // Extract real parts
        return result.map { plane ->
            plane.map { row ->
                row.map { complex -> complex.real }.toDoubleArray()
            }.toTypedArray()
        }.toTypedArray()
    }

    // DerivativeOfFunction
    fun derivativeOfFunction(f: Array<Array<DoubleArray>>): Triple<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val vx = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val vy = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val vz = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    val ixp = (i + 1) % resx
                    val iyp = (j + 1) % resy
                    val izp = (k + 1) % resz
                    vx[i][j][k] = f[ixp][j][k] - f[i][j][k]
                    vy[i][j][k] = f[i][iyp][k] - f[i][j][k]
                    vz[i][j][k] = f[i][j][izp] - f[i][j][k]
                }
            }
        }
        return Triple(vx, vy, vz)
    }

    // DerivativeOfOneForm
    fun derivativeOfOneForm(vx: Array<Array<DoubleArray>>, vy: Array<Array<DoubleArray>>, vz: Array<Array<DoubleArray>>): Triple<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val wx = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val wy = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val wz = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    val ixp = (i + 1) % resx
                    val iyp = (j + 1) % resy
                    val izp = (k + 1) % resz
                    wx[i][j][k] = vy[i][j][k] - vy[i][j][izp] + vz[i][iyp][k] - vz[i][j][k]
                    wy[i][j][k] = vz[i][j][k] - vz[ixp][j][k] + vx[i][j][izp] - vx[i][j][k]
                    wz[i][j][k] = vx[i][j][k] - vx[i][iyp][k] + vy[ixp][j][k] - vy[i][j][k]
                }
            }
        }
        return Triple(wx, wy, wz)
    }

    // DerivativeOfTwoForm
    fun derivativeOfTwoForm(wx: Array<Array<DoubleArray>>, wy: Array<Array<DoubleArray>>, wz: Array<Array<DoubleArray>>): Array<Array<DoubleArray>> {
        val f = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    val ixp = (i + 1) % resx
                    val iyp = (j + 1) % resy
                    val izp = (k + 1) % resz
                    f[i][j][k] = (wx[ixp][j][k] - wx[i][j][k]) + (wy[i][iyp][k] - wy[i][j][k]) + (wz[i][j][izp] - wz[i][j][k])
                }
            }
        }
        return f
    }

    // Div
    fun div(vx: Array<Array<Array<Double>>>, vy: Array<Array<Array<Double>>>, vz: Array<Array<Array<Double>>>): Array<Array<DoubleArray>> {
        val f = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    val ixm = (i - 2 + resx) % resx
                    val iym = (j - 2 + resy) % resy
                    val izm = (k - 2 + resz) % resz
                    f[i][j][k] = (vx[i][j][k] - vx[ixm][j][k]) / dx.pow(2) +
                        (vy[i][j][k] - vy[i][iym][k]) / dy.pow(2) +
                        (vz[i][j][k] - vz[i][j][izm]) / dz.pow(2)
                }
            }
        }
        return f
    }

    // Sharp
    fun sharp(vx: Array<Array<DoubleArray>>, vy: Array<Array<DoubleArray>>, vz: Array<Array<DoubleArray>>): Triple<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val ux = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val uy = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val uz = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i in 0 until resx) {
            for (j in 0 until resy) {
                for (k in 0 until resz) {
                    val ixm = (i - 2 + resx) % resx
                    val iym = (j - 2 + resy) % resy
                    val izm = (k - 2 + resz) % resz
                    ux[i][j][k] = 0.5 * (vx[ixm][j][k] + vx[i][j][k]) / dx
                    uy[i][j][k] = 0.5 * (vy[i][iym][k] + vy[i][j][k]) / dy
                    uz[i][j][k] = 0.5 * (vz[i][j][izm] + vz[i][j][k]) / dz
                }
            }
        }
        return Triple(ux, uy, uz)
    }

    fun staggeredSharp(
        vx: Array<Array<Array<Double>>>,
        vy: Array<Array<Array<Double>>>,
        vz: Array<Array<Array<Double>>>
    ): Triple<Array<Array<Array<Double>>>, Array<Array<Array<Double>>>, Array<Array<Array<Double>>>> {

        val ux = vx.map { layer ->
            layer.map { row ->
                row.map { value ->
                    value / dx
                }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()

        val uy = vy.map { layer ->
            layer.map { row ->
                row.map { value ->
                    value / dy
                }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()

        val uz = vz.map { layer ->
            layer.map { row ->
                row.map { value ->
                    value / dz
                }.toTypedArray()
            }.toTypedArray()
        }.toTypedArray()

        return Triple(ux, uy, uz)
    }

}



