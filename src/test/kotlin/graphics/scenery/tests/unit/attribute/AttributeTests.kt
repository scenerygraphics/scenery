package graphics.scenery.tests.unit.attribute

import graphics.scenery.DefaultNode
import graphics.scenery.Node
import graphics.scenery.attribute.DefaultAttributesMap
import graphics.scenery.attribute.geometry.DefaultGeometry
import graphics.scenery.attribute.geometry.Geometry
import graphics.scenery.attribute.geometry.HasGeometry
import graphics.scenery.attribute.material.DefaultMaterial
import graphics.scenery.attribute.material.DelegatesMaterial
import graphics.scenery.attribute.material.HasMaterial
import graphics.scenery.attribute.material.Material
import graphics.scenery.attribute.renderable.DefaultRenderable
import graphics.scenery.attribute.renderable.HasRenderable
import graphics.scenery.attribute.renderable.Renderable
import graphics.scenery.attribute.spatial.DefaultSpatial
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.attribute.spatial.Spatial
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributeTests {

    @Test
    fun testDefaultAttributesMap() {
        val map = DefaultAttributesMap()
        assertNull(map.get(Material::class.java))
        map.put(Material::class.java, DefaultMaterial())
        assertNotNull(map.get(Material::class.java))
    }

    @Test
    fun testDefaultNodeMaterial() {
        val node = DefaultNode()
        assertNull(node.materialOrNull())
        var blockCalled = false
        assertNull(node.ifMaterial {
            blockCalled = true
        })
        assertFalse(blockCalled)
        node.setMaterial(DefaultMaterial())
        assertNotNull(node.materialOrNull())
        assertNotNull(node.ifMaterial {
            blockCalled = true
        })
        assertTrue(blockCalled)
    }

    @Test
    fun testDefaultNodeSpatial() {
        val node = DefaultNode()
        assertNull(node.spatialOrNull())
        var blockCalled = false
        assertNull(node.ifSpatial {
            blockCalled = true
        })
        assertFalse(blockCalled)
        node.addAttribute(Spatial::class.java, DefaultSpatial(node))
        assertNotNull(node.spatialOrNull())
        assertNotNull(node.ifSpatial {
            blockCalled = true
        })
        assertTrue(blockCalled)
    }

    @Test
    fun testDefaultNodeRenderable() {
        val node = DefaultNode()
        assertNull(node.renderableOrNull())
        var blockCalled = false
        assertNull(node.ifRenderable {
            blockCalled = true
        })
        assertFalse(blockCalled)
        node.addAttribute(Renderable::class.java, DefaultRenderable(node))
        assertNotNull(node.renderableOrNull())
        assertNotNull(node.ifRenderable {
            blockCalled = true
        })
        assertTrue(blockCalled)
    }

    @Test
    fun testDefaultNodeGeometry() {
        val node = DefaultNode()
        assertNull(node.geometryOrNull())
        var blockCalled = false
        assertNull(node.ifGeometry {
            blockCalled = true
        })
        assertFalse(blockCalled)
        node.addAttribute(Geometry::class.java, DefaultGeometry(node))
        assertNotNull(node.geometryOrNull())
        assertNotNull(node.ifGeometry {
            blockCalled = true
        })
        assertTrue(blockCalled)
    }

    @Test
    fun testHasGeometry() {
        val node = NodeWithGeometry()
        var exceptionThrown = false
        try{
            node.geometryOrNull()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
        node.addGeometry()
        assertNotNull(node.geometryOrNull())
        assertNotNull(node.geometry())
    }

    @Test
    fun testHasRenderable() {
        val node = NodeWithRenderable()
        var exceptionThrown = false
        try{
            node.renderableOrNull()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
        node.addRenderable()
        assertNotNull(node.renderableOrNull())
        assertNotNull(node.renderable())
    }

    @Test
    fun testHasSpatial() {
        val node = NodeWithSpatial()
        var exceptionThrown = false
        try{
            node.spatialOrNull()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
        node.addSpatial()
        assertNotNull(node.spatialOrNull())
        assertNotNull(node.spatial())
    }

    @Test
    fun testHasMaterial() {
        val node = NodeWithMaterial()
        var exceptionThrown = false
        try{
            node.materialOrNull()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown)
        node.addMaterial()
        assertNotNull(node.materialOrNull())
        assertNotNull(node.material())
    }

    @Test
    fun testDelegatesMaterial() {
        val node = NodeWithMaterial()
        node.addMaterial()
        val nodeWithoutMaterial = NodeWithDelegateMaterial()
        nodeWithoutMaterial.setDelegate(node)
        assertNotNull(node.materialOrNull())
        assertNotNull(node.material())
    }

    internal class NodeWithGeometry : DefaultNode(), HasGeometry
    internal class NodeWithRenderable : DefaultNode(), HasRenderable
    internal class NodeWithSpatial : DefaultNode(), HasSpatial
    internal class NodeWithMaterial : DefaultNode(), HasMaterial

    internal class NodeWithDelegateMaterial : DefaultNode(), DelegatesMaterial {
        private var mydelegate: Node? = null
        override fun getDelegateMaterial(): Material? {
            if(mydelegate != null) return mydelegate!!.materialOrNull()
            else return null
        }
        fun setDelegate(node: Node) {
            mydelegate = node
        }
    }

}
