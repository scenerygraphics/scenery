package scenery

import cleargl.GLVector
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by ulrik on 29/01/16.
 */
open class Material {
    var name: String = "Material"
    var diffuse: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    var specular: GLVector = GLVector(0.5f, 0.5f, 0.5f)
    var ambient: GLVector = GLVector(0.5f, 0.5f, 0.5f)

    var opacity = 1.0f
    var transparent: Boolean = false
    @Volatile var textures: ConcurrentHashMap<String, String> = ConcurrentHashMap()
    @Volatile var transferTextures: ConcurrentHashMap<String, GenericTexture> = ConcurrentHashMap()
    var doubleSided: Boolean = false

    var needsTextureReload: Boolean = false

    companion object Factory {
       fun DefaultMaterial() {
          val m = Material()
       }
    }
}