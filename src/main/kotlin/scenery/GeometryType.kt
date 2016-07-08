package scenery

/**
 * Enum class storing the geometry type, e.g. of a [Node]
 *
 * [DeferredLightingRenderer] e.g. has extension functions to convert these types
 * to OpenGL geometry types.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
enum class GeometryType {
    TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN, POLYGON, POINTS, LINE
}
