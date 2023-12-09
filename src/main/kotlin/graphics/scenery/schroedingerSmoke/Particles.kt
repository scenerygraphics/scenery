package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.linear.ArrayRealVector
import kotlin.math.floor


class Particles {
    // Using Apache Commons Math's ArrayRealVector for positions
    var x: ArrayRealVector
    var y: ArrayRealVector
    var z: ArrayRealVector

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
        fun staggeredVelocity(px: ArrayRealVector, py: ArrayRealVector, pz: ArrayRealVector,
                              torus: TorusDEC, vx: ArrayRealVector, vy: ArrayRealVector, vz: ArrayRealVector)
        :Triple<ArrayRealVector, ArrayRealVector, ArrayRealVector> {
            // Adjust positions to be within the bounds of the grid
            val modPx = ArrayRealVector(px.toArray().map { it % torus.sizex }.toDoubleArray())
            val modPy = ArrayRealVector(py.toArray().map { it % torus.sizey }.toDoubleArray())
            val modPz = ArrayRealVector(pz.toArray().map { it % torus.sizez }.toDoubleArray())

            // Calculate indices as integer values
            val ix = Array(px.dimension) { index -> floor(modPx.getEntry(index) / torus.dx).toInt() }
            val iy = Array(py.dimension) { index -> floor(modPy.getEntry(index) / torus.dy).toInt() }
            val iz = Array(pz.dimension) { index -> floor(modPz.getEntry(index) / torus.dz).toInt() }

            val ixp = ix.map { (it % torus.resx) }
            val iyp = iy.map { (it % torus.resy) }
            val izp = iz.map { (it % torus.resz) }

            // Placeholder for sub2ind-like operations and fetching values from vx, vy, vz
            // Assuming sub2indFunc returns the appropriate index in the 1D velocity array based on 3D indices
            val ind0 = sub2indFunc(ix.toList(), iy.toList(), iz.toList(), torus)
            val indxp = sub2indFunc(ixp, iy.toList(), iz.toList(), torus)
            val indyp = sub2indFunc(ix.toList(), iyp, iz.toList(), torus)
            val indzp = sub2indFunc(ix.toList(), iy.toList(), izp, torus)
            val indxpyp = sub2indFunc(ixp, iyp, iz.toList(), torus)
            val indypzp = sub2indFunc(ix.toList(), iyp, izp, torus)
            val indxpzp = sub2indFunc(ixp, iy.toList(), izp, torus)

            // Calculate weights
            val wx = modPx.toArray().mapIndexed { index, value -> value - (ix[index] * torus.dx) }
            val wy = modPy.toArray().mapIndexed { index, value -> value - (iy[index] * torus.dy) }
            val wz = modPz.toArray().mapIndexed { index, value -> value - (iz[index] * torus.dz) }

            return trilinearInterpolate(vx, vy, vz, wx, wy, wz,
                ind0, indxp, indyp, indzp, indxpyp, indypzp, indxpzp)
        }

        fun sub2indFunc(ix: List<Int>, iy: List<Int>, iz: List<Int>, torus: TorusDEC): IntArray {
            // Assuming the data is stored in a row-major order
            return IntArray(ix.size) { index ->
                iz[index] * (torus.resx * torus.resy) + iy[index] * torus.resx + ix[index]
            }
        }


        fun trilinearInterpolate(
            vx: ArrayRealVector, vy: ArrayRealVector, vz: ArrayRealVector,
            wx: List<Double>, wy: List<Double>, wz: List<Double>,
            ind0: IntArray, indxp: IntArray, indyp: IntArray,
            indzp: IntArray, indxpyp: IntArray, indypzp: IntArray, indxpzp: IntArray
        ): Triple<ArrayRealVector, ArrayRealVector, ArrayRealVector> {

            val uxValues = DoubleArray(wx.size)
            val uyValues = DoubleArray(wx.size)
            val uzValues = DoubleArray(wx.size)

            for (i in wx.indices) {
                uxValues[i] = (1 - wz[i]) *
                    ((1 - wy[i]) * vx.getEntry(ind0[i]) + wy[i] * vx.getEntry(indyp[i])) +
                    wz[i] * ((1 - wy[i]) * vx.getEntry(indzp[i]) + wy[i] * vx.getEntry(indypzp[i]))
                uyValues[i] = (1 - wz[i]) *
                    ((1 - wx[i]) * vy.getEntry(ind0[i]) + wx[i] * vy.getEntry(indxp[i])) +
                    wz[i] * ((1 - wx[i]) * vy.getEntry(indzp[i]) + wx[i] * vy.getEntry(indxpzp[i]))
                uzValues[i] = (1 - wy[i]) * ((1 - wx[i]) *
                    vz.getEntry(ind0[i]) + wx[i] * vz.getEntry(indxp[i])) +
                    wy[i] * ((1 - wx[i]) * vz.getEntry(indyp[i]) + wx[i] * vz.getEntry(indxpyp[i]))
            }

            val ux = ArrayRealVector(uxValues)
            val uy = ArrayRealVector(uyValues)
            val uz = ArrayRealVector(uzValues)

            return Triple(ux, uy, uz)
        }
    }
}

