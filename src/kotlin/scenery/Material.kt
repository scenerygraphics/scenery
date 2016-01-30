package scenery

import cleargl.GLVector

/**
 * Created by ulrik on 29/01/16.
 */
interface Material {
    var ambient: GLVector
    var diffuse: GLVector
    var specular: GLVector
}