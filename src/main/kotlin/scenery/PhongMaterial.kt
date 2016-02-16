package scenery

import cleargl.GLVector

/**
 * Created by ulrik on 29/01/16.
 */
class PhongMaterial : Material {
    override var diffuse: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    override var specular: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    override var ambient: GLVector = GLVector(0.5f, 0.5f, 0.5f)

    companion object Factory {
       fun DefaultMaterial() {
          val m = PhongMaterial()
       }
    }
}