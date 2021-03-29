package graphics.scenery.geometry

/**
 * Enum class storing the geometry type, e.g. of a [Node]
 *
 * [DeferredLightingRenderer] e.g. has extension functions to convert these types
 * to OpenGL geometry types.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
enum class GeometryType {
    /** Triangles: Any three adjacent vertices will be connected. */
    TRIANGLES,
    /** Triangle strip: Draws in the order v0, v1, v2 ... v2, v1, v3 ... v2, v3, v4 ... etc */
    TRIANGLE_STRIP,
    /** Triangle fan: Triangles share central point: v0, v1, v2 ... v0, v3, v4 ... v0, v5, v6 */
    TRIANGLE_FAN,
    /** Polygon: Needs to be tesselated before drawing. */
    POLYGON,
    /** Draw vertices as simple points. */
    POINTS,
    /** Draw vertices as lines, where every two vertices constitute a line segment. */
    LINE,
    /** Draw vertices as lines, with adjacency information. */
    LINES_ADJACENCY,
    /** Draw vertices as line strips, with adjacency information. */
    LINE_STRIP_ADJACENCY
}
