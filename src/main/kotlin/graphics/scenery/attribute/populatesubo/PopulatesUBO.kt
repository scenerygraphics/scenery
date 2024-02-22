package graphics.scenery.attribute.populatesubo

import graphics.scenery.backends.UBO

interface PopulatesUBO {
    fun populate(ubo: UBO)
}