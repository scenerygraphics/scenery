package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.complex.Complex
import org.jtransforms.fft.DoubleFFT_3D
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

    open val dx = sizex.toDouble() / resx
    open val dy = sizey.toDouble() / resy
    open val dz = sizez.toDouble() / resz

    open val ix: IntArray = IntArray(resx) { it }
    open val iy: IntArray = IntArray(resy) { it }
    open val iz: IntArray = IntArray(resz) { it }

    open val px: Array<Array<DoubleArray>> = Array(resx) { iix -> Array(resy) { iiy -> DoubleArray(resz) { iiz -> iix * dx } } }
    open val py: Array<Array<DoubleArray>> = Array(resx) { Array(resy) { iiy -> DoubleArray(resz) { iiz -> iiy * dy } } }
    open val pz: Array<Array<DoubleArray>> = Array(resx) { Array(resy) { iiy -> DoubleArray(resz) { iiz -> iiz * dz } } }

    fun poissonSolve(f: Array<Array<DoubleArray>>): Array<Array<Array<Complex>>> {
        val nx = resx
        val ny = resy
        val nz = resz

        // Flattening the 3D array into a single-dimensional array for JTransforms.
        // The size of the last dimension is 2 * (nz / 2 + 1) for the real+imaginary parts in packed format.
        val data = DoubleArray(nx * ny * 2 * (nz / 2 + 1))
        for (i in 0 until nx) {
            for (j in 0 until ny) {
                for (k in 0 until nz) {
                    data[(i * ny * 2 * (nz / 2 + 1)) + (j * 2 * (nz / 2 + 1)) + k] = f[i][j][k]
                }
            }
        }

        // Perform the forward FFT.
        val fft3d = DoubleFFT_3D(nx.toLong(), ny.toLong(), nz.toLong())
        fft3d.realForward(data)

        // Create wave number grids
        val sx = Array(nx) { i -> Array(ny) { j -> DoubleArray(nz) { k -> sin(Math.PI * i / nx) / dx } } }
        val sy = Array(nx) { i -> Array(ny) { j -> DoubleArray(nz) { k -> sin(Math.PI * j / ny) / dy } } }
        val sz = Array(nx) { i -> Array(ny) { j -> DoubleArray(nz) { k -> sin(Math.PI * k / nz) / dz } } }

        // Apply the spectral method operation
        for (i in 0 until nx) {
            for (j in 0 until ny) {
                for (k in 0 until (nz / 2 + 1)) { // Half the frequency components for the last dimension
                    val realIndex = (i * ny * (nz / 2 + 1) + j * (nz / 2 + 1) + k) * 2
                    val imagIndex = realIndex + 1

                    val sxVal = sx[i][j][k]
                    val syVal = sy[i][j][k]
                    val szVal = if (k * 2 < nz) sz[i][j][k] else -sz[i][j][nz - k] // Handle the symmetry

                    val denom = sxVal.pow(2) + syVal.pow(2) + szVal.pow(2)
                    if (denom != 0.0) { // Avoid division by zero
                        val factor = -0.25 / denom

                        // Apply the factor to both real and imaginary parts
                        data[realIndex] *= factor
                        data[imagIndex] *= factor
                    }
                }
            }
        }

        // Perform the inverse FFT
        fft3d.realInverse(data, true)

        // Convert the data back to a 3D array of Complex numbers
        val result = Array(nx) { Array(ny) { Array(nz) { Complex.ZERO } } }
        for (i in 0 until nx) {
            for (j in 0 until ny) {
                for (k in 0 until nz) {
                    val realPart = data[(i * ny * 2 * (nz / 2 + 1)) + (j * 2 * (nz / 2 + 1)) + k]
                    // The imaginary part is zero since the inverse is to real
                    result[i][j][k] = Complex(realPart, 0.0)
                }
            }
        }


        return result
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
                    // Note: In MATLAB, the -2 + 1 effectively makes it -1.
                    // So in Kotlin, we subtract 1 and then add resx to ensure positive indices.
                    val ixm = (i - 1 + resx) % resx
                    val iym = (j - 1 + resy) % resy
                    val izm = (k - 1 + resz) % resz

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



