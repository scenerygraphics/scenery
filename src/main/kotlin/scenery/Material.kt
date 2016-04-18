package scenery

import cleargl.GLVector
import java.util.*

/**
 * Created by ulrik on 29/01/16.
 */
open class Material {
    var name: String = "Material"
    var diffuse: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    var specular: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    var ambient: GLVector = GLVector(0.5f, 0.5f, 0.5f)

    var opacity = 1.0f
    var textures: HashMap<String, String> = HashMap()
    var doubleSided: Boolean = false

    companion object Factory {
       fun DefaultMaterial() {
          val m = Material()
       }
    }
}