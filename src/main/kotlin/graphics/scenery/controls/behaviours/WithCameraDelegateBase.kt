package graphics.scenery.controls.behaviours

import graphics.scenery.Camera
import kotlin.reflect.KProperty

open class WithCameraDelegateBase(protected val camera: () -> Camera?) {
    /** The [graphics.scenery.Node] this behaviour class controls */
    protected val cam: Camera? by CameraDelegate()

    /** Camera delegate class, converting lambdas to Cameras. */
    protected inner class CameraDelegate {
        /** Returns the [graphics.scenery.Camera] resulting from the evaluation of [camera] */
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Camera? {
            return camera.invoke()
        }

        /** Setting the value is not supported */
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Camera?) {
            throw UnsupportedOperationException()
        }
    }
}
