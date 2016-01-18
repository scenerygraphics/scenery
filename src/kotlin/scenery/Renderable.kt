package scenery

import cleargl.GLMatrix
import cleargl.GLProgram
import cleargl.GLVector
import cleargl.RendererInterface
import com.jogamp.opengl.math.Quaternion

interface Renderable {
    var program: GLProgram?
    var model: GLMatrix
    var imodel: GLMatrix

    var view: GLMatrix?
    var iview: GLMatrix?
    var projection: GLMatrix?
    var iprojection: GLMatrix?
    var modelView: GLMatrix?
    var imodelView: GLMatrix?
    var mvp: GLMatrix?

    var position: GLVector?
    var scale: GLVector?
    var rotation: Quaternion

    var initialized: Boolean
    var dirty: Boolean
    var renderer: RendererInterface?
    var visible: Boolean

    fun init(): Boolean
}
