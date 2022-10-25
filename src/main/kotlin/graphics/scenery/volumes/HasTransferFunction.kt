package graphics.scenery.volumes

interface HasTransferFunction {

    var transferFunction : TransferFunction
    var minDisplayRange : Float
    var maxDisplayRange : Float
}
