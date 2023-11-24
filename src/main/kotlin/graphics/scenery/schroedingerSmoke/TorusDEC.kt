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






}



