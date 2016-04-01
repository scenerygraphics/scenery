package scenery

import cleargl.GLVector
import java.util.*

/**
 * Created by ulrik on 29/01/16.
 */
open class Material {
    var diffuse: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    var specular: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    var ambient: GLVector = GLVector(0.5f, 0.5f, 0.5f)

    var opacity = 1.0f
    var textures: ArrayList<String> = ArrayList()

    companion object Factory {
       fun DefaultMaterial() {
          val m = Material()
       }
    }
}