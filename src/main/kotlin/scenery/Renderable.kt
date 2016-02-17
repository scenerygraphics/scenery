package scenery

import cleargl.GLMatrix
import cleargl.GLVector
import com.jogamp.opengl.math.Quaternion

interface Renderable {
    var model: GLMatrix
    var imodel: GLMatrix

    var world: GLMatrix
    var iworld: GLMatrix

    var view: GLMatrix?
    var iview: GLMatrix?
    var projection: GLMatrix?
    var iprojection: GLMatrix?
    var modelView: GLMatrix?
    var imodelView: GLMatrix?
    var mvp: GLMatrix?

    var position: GLVector
    var scale: GLVector
    var rotation: Quaternion

    var initialized: Boolean
    var dirty: Boolean
    var visible: Boolean

    var material: Material?

    fun init(): Boolean
}
