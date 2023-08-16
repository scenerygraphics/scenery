package graphics.scenery.volumes

/**
 * @author Konrad Michel <Konrad.Michel@mailbox.tu-dresden.de>
 *
 *     Interface to abstract out the basic parameters of a TransferFunction use case.
 */
interface HasTransferFunction {

    var transferFunction : TransferFunction
    var minDisplayRange : Float
    var maxDisplayRange : Float
    var range: Pair<Float, Float>
}
