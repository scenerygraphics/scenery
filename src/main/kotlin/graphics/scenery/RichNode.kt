package graphics.scenery

import graphics.scenery.attribute.renderable.HasRenderable
import graphics.scenery.attribute.material.HasMaterial
import graphics.scenery.attribute.spatial.HasSpatial
import graphics.scenery.volumes.Volume
import graphics.scenery.volumes.Volume.Companion.fromPathRawSplit
import org.joml.Vector3f

open class RichNode(override var name: String = "Node") : DefaultNode (name), HasRenderable, HasMaterial, HasSpatial {
    init {
        addRenderable()
        addMaterial()
        addSpatial()
    }

    /**
     * Positions [volumes] back-to-back without gaps, using their pixel-to-world ratio. Can, e.g., be used
     * with [fromPathRawSplit] to load volume files greater than 2 GiB into sliced partitions and place
     * the partitions back-to-back, emulating a single large volume in the scene.
     */
    fun positionVolumeSlices(volumes: List<Volume>) {
        val pixelToWorld = volumes.first().pixelToWorldRatio

        var sliceIndex = 0
        volumes.forEach { volume ->
            val currentSlices = volume.getDimensions().z
            logger.debug("Volume partition with z slices: $currentSlices")
            volume.pixelToWorldRatio = pixelToWorld

            volume.spatial().position = Vector3f(0f, 0f, 1.0f * (sliceIndex) * pixelToWorld)
            sliceIndex += currentSlices
        }
    }
}
