package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.linear.ArrayRealVector
import kotlin.math.floor


class Particles {
    // Using Apache Commons Math's ArrayRealVector for positions
    var x: ArrayRealVector
    var y: ArrayRealVector
    var z: ArrayRealVector
    //TODO create a positions array that contains all particle positions as Vector3f. Needs to be as simple as possible and as performant as necessary

    // Default constructor
    init {
        x = ArrayRealVector()
        y = ArrayRealVector()
        z = ArrayRealVector()

    }

    // Advect particle positions using RK4 in a grid torus with staggered velocity vx, vy, vz, for dt period of time
    fun staggeredAdvect(
        torus: TorusDEC,
        vx: Array<Array<Array<Double>>>,
        vy: Array<Array<Array<Double>>>,
        vz: Array<Array<Array<Double>>>,
        dt: Double
    ) {
        val new_x = ArrayRealVector(x.dimension)
        val new_y = ArrayRealVector(y.dimension)
        val new_z = ArrayRealVector(z.dimension)

        for (i in 0 until x.dimension) {
            val particleX = x.getEntry(i)
            val particleY = y.getEntry(i)
            val particleZ = z.getEntry(i)

            val (k1x, k1y, k1z) = staggeredVelocity(particleX, particleY, particleZ, torus, vx, vy, vz)
            val (k2x, k2y, k2z) = staggeredVelocity(particleX + k1x * dt / 2, particleY + k1y * dt / 2, particleZ + k1z * dt / 2, torus, vx, vy, vz)
            val (k3x, k3y, k3z) = staggeredVelocity(particleX + k2x * dt / 2, particleY + k2y * dt / 2, particleZ + k2z * dt / 2, torus, vx, vy, vz)
            val (k4x, k4y, k4z) = staggeredVelocity(particleX + k3x * dt, particleY + k3y * dt, particleZ + k3z * dt, torus, vx, vy, vz)

            new_x.setEntry(i, particleX + dt / 6 * (k1x + 2 * k2x + 2 * k3x + k4x))
            new_y.setEntry(i, particleY + dt / 6 * (k1y + 2 * k2y + 2 * k3y + k4y))
            new_z.setEntry(i, particleZ + dt / 6 * (k1z + 2 * k2z + 2 * k3z + k4z))
        }

        x = new_x
        y = new_y
        z = new_z
    }

    // For removing particles
    fun keep(indices: IntArray) {
        x = ArrayRealVector(indices.map { x.getEntry(it) }.toDoubleArray())
        y = ArrayRealVector(indices.map { y.getEntry(it) }.toDoubleArray())
        z = ArrayRealVector(indices.map { z.getEntry(it) }.toDoubleArray())
    }

    companion object {
        fun staggeredVelocity(
            px: Double, py: Double, pz: Double,
            torus: TorusDEC,
            vx: Array<Array<Array<Double>>>,
            vy: Array<Array<Array<Double>>>,
            vz: Array<Array<Array<Double>>>
        ): Triple<Double, Double, Double> {

            // Adjust positions to be within the bounds of the grid
            val modPx = px % torus.sizex
            val modPy = py % torus.sizey
            val modPz = pz % torus.sizez

            // Calculate indices
            val ix = floor(modPx / torus.dx).toInt()
            val iy = floor(modPy / torus.dy).toInt()
            val iz = floor(modPz / torus.dz).toInt()
            val ixp = (ix + 1) % torus.resx
            val iyp = (iy + 1) % torus.resy
            val izp = (iz + 1) % torus.resz

            // Calculate weights
            val wx = modPx - ix * torus.dx
            val wy = modPy - iy * torus.dy
            val wz = modPz - iz * torus.dz

            // Perform trilinear interpolation
            return trilinearInterpolate(vx, vy, vz, ix, iy, iz, ixp, iyp, izp, wx, wy, wz)
        }

        fun trilinearInterpolate(
            vx: Array<Array<Array<Double>>>,
            vy: Array<Array<Array<Double>>>,
            vz: Array<Array<Array<Double>>>,
            ix: Int, iy: Int, iz: Int,
            ixp: Int, iyp: Int, izp: Int,
            wx: Double, wy: Double, wz: Double
        ): Triple<Double, Double, Double> {

            // Ensure indices are within the array bounds
            val safeIx = ix.coerceIn(0 until vx.size)
            val safeIy = iy.coerceIn(0 until vx[0].size)
            val safeIz = iz.coerceIn(0 until vx[0][0].size)
            val safeIxp = ixp.coerceIn(0 until vx.size)
            val safeIyp = iyp.coerceIn(0 until vx[0].size)
            val safeIzp = izp.coerceIn(0 until vx[0][0].size)

            // Fetch the relevant values for vx, vy, vz
            val v000 = vx[safeIx][safeIy][safeIz]
            val v100 = vx[safeIxp][safeIy][safeIz]
            val v010 = vx[safeIx][safeIyp][safeIz]
            val v001 = vx[safeIx][safeIy][safeIzp]
            val v101 = vx[safeIxp][safeIy][safeIzp]
            val v011 = vx[safeIx][safeIyp][safeIzp]
            val v110 = vx[safeIxp][safeIyp][safeIz]
            val v111 = vx[safeIxp][safeIyp][safeIzp]

            // Trilinear interpolation for vx
            val vxInterpolated = v000 * (1 - wx) * (1 - wy) * (1 - wz) +
                v100 * wx * (1 - wy) * (1 - wz) +
                v010 * (1 - wx) * wy * (1 - wz) +
                v001 * (1 - wx) * (1 - wy) * wz +
                v101 * wx * (1 - wy) * wz +
                v011 * (1 - wx) * wy * wz +
                v110 * wx * wy * (1 - wz) +
                v111 * wx * wy * wz

            // Fetch the relevant values for vy
            val vy000 = vy[safeIx][safeIy][safeIz]
            val vy100 = vy[safeIxp][safeIy][safeIz]
            val vy010 = vy[safeIx][safeIyp][safeIz]
            val vy001 = vy[safeIx][safeIy][safeIzp]
            val vy101 = vy[safeIxp][safeIy][safeIzp]
            val vy011 = vy[safeIx][safeIyp][safeIzp]
            val vy110 = vy[safeIxp][safeIyp][safeIz]
            val vy111 = vy[safeIxp][safeIyp][safeIzp]

            // Trilinear interpolation for vy
            val vyInterpolated = vy000 * (1 - wx) * (1 - wy) * (1 - wz) +
                vy100 * wx * (1 - wy) * (1 - wz) +
                vy010 * (1 - wx) * wy * (1 - wz) +
                vy001 * (1 - wx) * (1 - wy) * wz +
                vy101 * wx * (1 - wy) * wz +
                vy011 * (1 - wx) * wy * wz +
                vy110 * wx * wy * (1 - wz) +
                vy111 * wx * wy * wz

            // Fetch the relevant values for vz
            val vz000 = vz[safeIx][safeIy][safeIz]
            val vz100 = vz[safeIxp][safeIy][safeIz]
            val vz010 = vz[safeIx][safeIyp][safeIz]
            val vz001 = vz[safeIx][safeIy][safeIzp]
            val vz101 = vz[safeIxp][safeIy][safeIzp]
            val vz011 = vz[safeIx][safeIyp][safeIzp]
            val vz110 = vz[safeIxp][safeIyp][safeIz]
            val vz111 = vz[safeIxp][safeIyp][safeIzp]

            // Trilinear interpolation for vz
            val vzInterpolated = vz000 * (1 - wx) * (1 - wy) * (1 - wz) +
                vz100 * wx * (1 - wy) * (1 - wz) +
                vz010 * (1 - wx) * wy * (1 - wz) +
                vz001 * (1 - wx) * (1 - wy) * wz +
                vz101 * wx * (1 - wy) * wz +
                vz011 * (1 - wx) * wy * wz +
                vz110 * wx * wy * (1 - wz) +
                vz111 * wx * wy * wz


            return Triple(vxInterpolated, vyInterpolated, vzInterpolated)
        }
    }
}

