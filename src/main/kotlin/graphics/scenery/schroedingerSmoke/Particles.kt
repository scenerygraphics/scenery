package graphics.scenery.schroedingerSmoke

class Particles {
    // Array of positions
    var x: DoubleArray
    var y: DoubleArray
    var z: DoubleArray

    // Default constructor
    init {
        // Initialize arrays, you might need to adjust the initialization as needed
        x = DoubleArray(0)
        y = DoubleArray(0)
        z = DoubleArray(0)
    }

    // Advect particle positions using RK4 in a grid torus with staggered velocity vx, vy, vz, for dt period of time
    fun staggeredAdvect(torus: TorusDEC, vx: DoubleArray, vy: DoubleArray, vz: DoubleArray, dt: Double) {
        val (k1x, k1y, k1z) = staggeredVelocity(x, y, z, torus, vx, vy, vz)
        val (k2x, k2y, k2z) = staggeredVelocity(x.mapIndexed { i, xi -> xi + k1x[i] * dt / 2 }.toDoubleArray(),
            y.mapIndexed { i, yi -> yi + k1y[i] * dt / 2 }.toDoubleArray(),
            z.mapIndexed { i, zi -> zi + k1z[i] * dt / 2 }.toDoubleArray(),
            torus, vx, vy, vz)
        val (k3x, k3y, k3z) = staggeredVelocity(x.mapIndexed { i, xi -> xi + k2x[i] * dt / 2 }.toDoubleArray(),
            y.mapIndexed { i, yi -> yi + k2y[i] * dt / 2 }.toDoubleArray(),
            z.mapIndexed { i, zi -> zi + k2z[i] * dt / 2 }.toDoubleArray(),
            torus, vx, vy, vz)
        val (k4x, k4y, k4z) = staggeredVelocity(x.mapIndexed { i, xi -> xi + k3x[i] * dt }.toDoubleArray(),
            y.mapIndexed { i, yi -> yi + k3y[i] * dt }.toDoubleArray(),
            z.mapIndexed { i, zi -> zi + k3z[i] * dt }.toDoubleArray(),
            torus, vx, vy, vz)

        x = x.mapIndexed { i, xi -> xi + dt / 6 * (k1x[i] + 2 * k2x[i] + 2 * k3x[i] + k4x[i]) }.toDoubleArray()
        y = y.mapIndexed { i, yi -> yi + dt / 6 * (k1y[i] + 2 * k2y[i] + 2 * k3y[i] + k4y[i]) }.toDoubleArray()
        z = z.mapIndexed { i, zi -> zi + dt / 6 * (k1z[i] + 2 * k2z[i] + 2 * k3z[i] + k4z[i]) }.toDoubleArray()
    }

    // For removing particles
    fun keep(ind: IntArray) {
        x = x.filterIndexed { i, _ -> i in ind }.toDoubleArray()
        y = y.filterIndexed { i, _ -> i in ind }.toDoubleArray()
        z = z.filterIndexed { i, _ -> i in ind }.toDoubleArray()
    }

    companion object {
        // Evaluates velocity at (px, py, pz) in the grid torus with staggered velocity vector field vx, vy, vz
        fun staggeredVelocity(px: DoubleArray, py: DoubleArray, pz: DoubleArray, torus: TorusDEC, vx: DoubleArray, vy: DoubleArray, vz: DoubleArray): Triple<DoubleArray, DoubleArray, DoubleArray> {
            // Placeholder for trilinear interpolation logic
            // Return interpolated velocities ux, uy, uz as Triple
            return Triple(DoubleArray(px.size) { 0.0 }, DoubleArray(py.size) { 0.0 }, DoubleArray(pz.size) { 0.0 })
        }
    }
}

