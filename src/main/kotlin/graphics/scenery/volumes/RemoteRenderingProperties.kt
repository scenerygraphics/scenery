package graphics.scenery.volumes

import graphics.scenery.DefaultNode
import graphics.scenery.net.Networkable

/**
 * A node used to synchronize remote rendering properties between server and client for remote volume
 * rendering.
 */
class RemoteRenderingProperties : DefaultNode("RemoteRenderingProperties"), Networkable {


    /**
     * The types of streaming supported for remote volume rendering.
     */
    enum class StreamType {
        VolumeRendering,
        VDI,
        None
    }

    /**
     * The streaming type used by this object currently.
     */
    var streamType : StreamType = StreamType.VolumeRendering
        set(value){
            field = value
            modifiedAt = System.nanoTime()
        }

    init {
        name = "RemoteRenderingProperties"
    }

    override fun update(fresh: Networkable, getNetworkable: (Int) -> Networkable, additionalData: Any?) {
        super.update(fresh, getNetworkable,additionalData)
        if (fresh !is RemoteRenderingProperties) throw IllegalArgumentException("Update called with object of foreign class")
        this.streamType = fresh.streamType
    }
}
