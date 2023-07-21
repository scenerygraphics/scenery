package graphics.scenery.tests.examples.volumes

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class VDIGenerationTest {
     /**
      * Tests VDI generation
      */
     @Test
     fun testGeneration() {
         //Step 1 : load the correct VDI files from desk
         val path = "/home/argupta/Desktop/scenery/src/test/resources/graphics/scenery/tests/unit/volumes"
         val testContent = File("${path}/Test_VDI_dump4").readBytes()
         val testColor = File("${path}/Test_VDI_4_ndc_col").readBytes()
         val testDepth = File("${path}/Test_VDI_4_ndc_depth").readBytes()
         val testOct = File("${path}/Test_VDI_4_ndc_octree").readBytes()

         //Step 2 : run VDI generation example and generate files
         VDIGenerationExample.main(emptyArray())

         //Step 3 : load just generated VDI files
         val content = File("VDI_dump4").readBytes()
         val color = File("VDI_4_ndc_col").readBytes()
         val depth = File("VDI_4_ndc_depth").readBytes()
         val oct = File("VDI_4_ndc_octree").readBytes()

         //Step 4 : create assertions to compare the files
         assertTrue(content.contentEquals(testContent))
         assertTrue(color.contentEquals(testColor))
         assertTrue(depth.contentEquals(testDepth))
         assertTrue(oct.contentEquals(testOct))

     }
}
