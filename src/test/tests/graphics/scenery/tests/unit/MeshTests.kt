package graphics.scenery.tests.unit

import graphics.scenery.Mesh
import graphics.scenery.Scene
import graphics.scenery.ShaderMaterial
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Tests for the [Mesh] class.
 *
 * @author Aryaman Gupta <aryaman1994@gmail.com>
 */
class MeshTests {
    private val logger by LazyLogger()

    @Test
    fun testReadFromOBJ() {
        logger.info("Testing mesh creation from OBJ file and bounding box calculation ...")
        val s = Scene()
        val erythrocyte = Mesh()
        erythrocyte.readFromOBJ(MeshTests::class.java.getResource("models/erythrocyte_simplified.obj").file)

        assertEquals(18000, erythrocyte.vertices.capacity())
        assertEquals(18000, erythrocyte.normals.capacity())
        assertEquals(3000, erythrocyte.indices.capacity())
        assertEquals(12000, erythrocyte.texcoords.capacity())

        assertEquals(-2.820594f, erythrocyte.boundingBox!!.min.x())
        assertEquals(-1.032556f, erythrocyte.boundingBox!!.min.y())
        assertEquals(-2.784652f, erythrocyte.boundingBox!!.min.z())

        assertEquals(2.825022f, erythrocyte.boundingBox!!.max.x())
        assertEquals(2.788384f, erythrocyte.boundingBox!!.max.y())
        assertEquals(1.63499f, erythrocyte.boundingBox!!.max.z())


        val leukocyte = Mesh()
        leukocyte.readFromOBJ(MeshTests::class.java.getResource("models/leukocyte_simplified.obj").file)

        assertEquals(144000, leukocyte.vertices.capacity())
        assertEquals(144000, leukocyte.normals.capacity())
        assertEquals(24000, leukocyte.indices.capacity())
        assertEquals(96000, leukocyte.texcoords.capacity())

        assertEquals(-1.121471f, leukocyte.boundingBox!!.min.x())
        assertEquals(-1.09877f, leukocyte.boundingBox!!.min.y())
        assertEquals(-1.087235f, leukocyte.boundingBox!!.min.z())

        assertEquals(1.121211f, leukocyte.boundingBox!!.max.x())
        assertEquals(1.096298f, leukocyte.boundingBox!!.max.y())
        assertEquals(1.109781f, leukocyte.boundingBox!!.max.z())
    }


    @Test
    fun testReadFromSTL() {
        logger.info("Testing mesh creation from STL file and bounding box calculation ...")
        val s = Scene()
        val erythrocyte = Mesh()
        erythrocyte.readFromSTL(MeshTests::class.java.getResource("models/erythrocyte_simplified.stl").file)

        assertEquals(9000, erythrocyte.vertices.capacity())
        assertEquals(9000, erythrocyte.normals.capacity())
        /*TODO:indices and texcoords?*/

        assertEquals(-2.820594f, erythrocyte.boundingBox!!.min.x())
        assertEquals(-1.032556f, erythrocyte.boundingBox!!.min.y())
        assertEquals(-2.784652f, erythrocyte.boundingBox!!.min.z())

        assertEquals(2.825022f, erythrocyte.boundingBox!!.max.x())
        assertEquals(2.788384f, erythrocyte.boundingBox!!.max.y())
        assertEquals(1.63499f, erythrocyte.boundingBox!!.max.z())
    }
}
