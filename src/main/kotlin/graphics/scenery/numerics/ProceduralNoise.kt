package graphics.scenery.numerics

interface ProceduralNoise {
    fun random1D(x: Float): Float
    fun random2D(x: Float, y: Float): Float
    fun random3D(x: Float, y: Float, z: Float): Float
    fun random4D(x: Float, y: Float, z: Float, w: Float): Float

    fun random1D(x: Double): Double
    fun random2D(x: Double, y: Double): Double
    fun random3D(x: Double, y: Double, z: Double): Double
    fun random4D(x: Double, y: Double, z: Double, w: Double): Double
}
