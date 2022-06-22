package graphics.scenery.tests.examples.advanced

import graphics.scenery.Hub
import graphics.scenery.controls.OpenXRHMD

class HololensOpenXRExample {
    init {
        val hub: Hub = Hub()
        val hololens = OpenXRHMD.hololens(hub)
        hololens.close()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            HololensOpenXRExample()
        }
    }
}
