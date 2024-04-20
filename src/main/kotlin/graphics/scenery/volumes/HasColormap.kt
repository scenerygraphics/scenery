package graphics.scenery.volumes

/**
 * @author Jan Tiemann <j.tiemann@hzdr.de>
 *
 * Interface for nodes (eg. volumes) that have a colormap to be editable by [ColormapPanel].
 */
interface HasColormap {
    var colormap: Colormap
}
