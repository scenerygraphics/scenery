package graphics.scenery.schroedingerSmoke

import org.joml.Vector3f
import kotlin.properties.Delegates

/**
    TorusDEC is a handle class that an instance is a 3D grid with periodic
    boundaries in x,y,z direction, i.e. a 3-torus. DEC stands for "Discrete
    Exterior Calculus", a set of operations including exterior derivatives,
    codifferentials.

    Derived from the supplementary material to:
    Albert Chern, Felix Knöppel, Ulrich Pinkall, Peter Schröder, and Steffen Weißmann. 2016.
    Schrödinger's smoke. ACM Trans. Graph. 35, 4, Article 77 (July 2016), 13 pages.
    https://doi.org/10.1145/2897824.2925868

 */

class TorusDEC(val sizex: Int, val sizey: Int, val sizez: Int, val resx: Int, val resy: Int, val resz: Int)
{
    private val dx = sizex.toFloat() / resx
    private val dy = sizey.toFloat() / resy
    private val dz = sizez.toFloat() / resz

    // Initialize the coordinate arrays
    val px = Array(resx) { ix -> ix * dx }
    val py = Array(resy) { iy -> iy * dy }
    val pz = Array(resz) { iz -> iz * dz }

    // not sure if we still need index arrays
    val ix = IntArray(resx) { it }
    val iy = IntArray(resy) { it }
    val iz = IntArray(resz) { it }

    //gradient of a scalar field f
    fun derivativeOfFunction(f: (Int, Int, Int) -> Double):
        Triple<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val vx = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val vy = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val vz = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (i_x in ix) {
            for (i_y in iy) {
                for (i_z in iz) {
                    val ixp = (ix[i_x] + 1) %(resx)
                    val iyp = (iy[i_y] + 1) %(resy)
                    val izp = (iz[i_z] + 1) %(resz)

                    vx[i_x][i_y][i_z] = f(ixp, i_y, i_z) - f(i_x, i_y, i_z)
                    vy[i_x][i_y][i_z] = f(i_x, iyp, i_z) - f(i_x, i_y, i_z)
                    vz[i_x][i_y][i_z] = f(i_x, i_y, izp) - f(i_x, i_y, i_z)
                }
            }
        }
        return Triple(vx, vy, vz)
    }

    fun derivativeOfOneForm(vx: Array<Array<Array<Double>>>, vy: Array<Array<Array<Double>>>, vz: Array<Array<Array<Double>>>):
        Triple<Array<Array<DoubleArray>>, Array<Array<DoubleArray>>, Array<Array<DoubleArray>>> {
        val wx = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val wy = Array(resx) { Array(resy) { DoubleArray(resz) } }
        val wz = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (ix in ix.indices) {
            for (iy in iy.indices) {
                for (iz in iz.indices) {
                    val ixp = (this.ix[ix] + 1) % resx
                    val iyp = (this.iy[iy] + 1) % resy
                    val izp = (this.iz[iz] + 1) % resz

                    wx[ix][iy][iz] = vy[ix][iy][iz] - vy[ix][iy][izp] + vz[ix][iyp][iz] - vz[ix][iy][iz]
                    wy[ix][iy][iz] = vz[ix][iy][iz] - vz[ixp][iy][iz] + vx[ix][iy][izp] - vx[ix][iy][iz]
                    wz[ix][iy][iz] = vx[ix][iy][iz] - vx[ix][iyp][iz] + vy[ixp][iy][iz] - vy[ix][iy][iz]
                }
            }
        }

        return Triple(wx, wy, wz)
    }

    fun derivativeOfTwoForm(wx: Array<Array<Array<Double>>>, wy: Array<Array<Array<Double>>>, wz: Array<Array<Array<Double>>>):
        Array<Array<DoubleArray>> {
        val f = Array(resx) { Array(resy) { DoubleArray(resz) } }

        for (ix in ix.indices) {
            for (iy in iy.indices) {
                for (iz in iz.indices) {
                    val ixp = (this.ix[ix] + 1) % resx
                    val iyp = (this.iy[iy] + 1) % resy
                    val izp = (this.iz[iz] + 1) % resz

                    f[ix][iy][iz] = (wx[ixp][iy][iz] - wx[ix][iy][iz]) + (wy[ix][iyp][iz] - wy[ix][iy][iz]) + (wz[ix][iy][izp] - wz[ix][iy][iz])
                }
            }
        }
        return f
    }





}



