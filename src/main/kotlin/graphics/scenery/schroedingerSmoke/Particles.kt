package graphics.scenery.schroedingerSmoke

import org.apache.commons.math3.linear.ArrayRealVector

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
    fun staggeredAdvect(torus: TorusDEC, vx: ArrayRealVector, vy: ArrayRealVector, vz: ArrayRealVector, dt: Double) {
        val (k1x, k1y, k1z) = staggeredVelocity(x, y, z, torus, vx, vy, vz)
        val (k2x, k2y, k2z) = staggeredVelocity(x.add(k1x.mapMultiply(dt / 2)),
            y.add(k1y.mapMultiply(dt / 2)),
            z.add(k1z.mapMultiply(dt / 2)),
            torus, vx, vy, vz)
        val (k3x, k3y, k3z) = staggeredVelocity(x.add(k2x.mapMultiply(dt / 2)),
            y.add(k2y.mapMultiply(dt / 2)),
            z.add(k2z.mapMultiply(dt / 2)),
            torus, vx, vy, vz)
        val (k4x, k4y, k4z) = staggeredVelocity(x.add(k3x.mapMultiply(dt)),
            y.add(k3y.mapMultiply(dt)),
            z.add(k3z.mapMultiply(dt)),
            torus, vx, vy, vz)

        x = x.add(k1x.mapMultiply(dt / 6))
            .add(k2x.mapMultiply(dt / 3))
            .add(k3x.mapMultiply(dt / 3))
            .add(k4x.mapMultiply(dt / 6))

        y = y.add(k1y.mapMultiply(dt / 6))
            .add(k2y.mapMultiply(dt / 3))
            .add(k3y.mapMultiply(dt / 3))
            .add(k4y.mapMultiply(dt / 6))

        z = z.add(k1z.mapMultiply(dt / 6))
            .add(k2z.mapMultiply(dt / 3))
            .add(k3z.mapMultiply(dt / 3))
            .add(k4z.mapMultiply(dt / 6))
    }

    // For removing particles
    fun keep(indices: IntArray) {
        x = ArrayRealVector(indices.map { x.getEntry(it) }.toDoubleArray())
        y = ArrayRealVector(indices.map { y.getEntry(it) }.toDoubleArray())
        z = ArrayRealVector(indices.map { z.getEntry(it) }.toDoubleArray())
    }

    companion object {
        // Evaluates velocity at (px, py, pz) in the grid torus with staggered velocity vector field vx, vy, vz
        fun staggeredVelocity(px: ArrayRealVector, py: ArrayRealVector, pz: ArrayRealVector, torus: TorusDEC, vx: ArrayRealVector, vy: ArrayRealVector, vz: ArrayRealVector): Triple<ArrayRealVector, ArrayRealVector, ArrayRealVector> {
            // Placeholder for trilinear interpolation logic
            // Return interpolated velocities ux, uy, uz as Triple
            return Triple(ArrayRealVector(px.dimension), ArrayRealVector(py.dimension), ArrayRealVector(pz.dimension))
        }
    }
}

