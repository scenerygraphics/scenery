package graphics.scenery.backends.vulkan

import graphics.scenery.utils.SceneryPanel

/**
 * Interface for swapchains to define whether they are [headless], or have a specific
 * [usageCondition], to prioritise them on construction.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
interface SwapchainParameters {
    var headless: Boolean
    var usageCondition: (SceneryPanel?) -> Boolean
}
