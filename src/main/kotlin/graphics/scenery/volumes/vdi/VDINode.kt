package graphics.scenery.volumes.vdi

import graphics.scenery.RichNode
import graphics.scenery.ShaderProperty
import org.joml.Matrix4f
import org.joml.Vector3f

class VDINode : RichNode() {

    @ShaderProperty
    var ProjectionOriginal = Matrix4f()

    @ShaderProperty
    var invProjectionOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal = Matrix4f()

    @ShaderProperty
    var invViewOriginal = Matrix4f()

    @ShaderProperty
    var ViewOriginal2 = Matrix4f()

    @ShaderProperty
    var invViewOriginal2 = Matrix4f()

    @ShaderProperty
    var useSecondBuffer = false

    @ShaderProperty
    var invModel = Matrix4f()

    @ShaderProperty
    var volumeDims = Vector3f()

    @ShaderProperty
    var nw = 0f

    @ShaderProperty
    var vdiWidth: Int = 0

    @ShaderProperty
    var vdiHeight: Int = 0

    @ShaderProperty
    var totalGeneratedSupsegs: Int = 0

    @ShaderProperty
    var do_subsample = false

    @ShaderProperty
    var max_samples = 50

    @ShaderProperty
    var sampling_factor = 0.1f

    @ShaderProperty
    var downImage = 1f

    @ShaderProperty
    var skip_empty = true

    @ShaderProperty
    var stratified_downsampling = false

    @ShaderProperty
    var printData = true
}
