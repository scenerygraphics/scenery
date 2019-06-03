package graphics.scenery.backends.vulkan

import graphics.scenery.utils.SceneryPanel

interface SwapchainParameters {
    var headless: Boolean
    var usageCondition: (SceneryPanel?) -> Boolean
}
