package scenery

interface HasGeometry {
    val vertexSize: Int
    val texcoordSize: Int
    val geometryType: GeometryType

    var vertices: FloatArray
    var normals: FloatArray
    var texcoords: FloatArray
    var indices: IntArray
}
