package graphics.scenery.numerics

/**
 * Interface for classes producing some kind of procedural noise,
 * continuous or not.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface ProceduralNoise {
    /** Generate 1D noise with the origin [x] */
    fun random1D(x: Float): Float
    /** Generate 2D noise with the origin [x],[y] */
    fun random2D(x: Float, y: Float): Float
    /** Generate 3D noise with the origin [x],[y],[z] */
    fun random3D(x: Float, y: Float, z: Float): Float
    /** Generate 4D noise with the origin [x],[y],[z],[w] */
    fun random4D(x: Float, y: Float, z: Float, w: Float): Float

    /** Generate 1D noise with the origin [x] */
    fun random1D(x: Double): Double
    /** Generate 2D noise with the origin [x],[y] */
    fun random2D(x: Double, y: Double): Double
    /** Generate 3D noise with the origin [x],[y],[z] */
    fun random3D(x: Double, y: Double, z: Double): Double
    /** Generate 4D noise with the origin [x],[y],[z],[w] */
    fun random4D(x: Double, y: Double, z: Double, w: Double): Double
}
