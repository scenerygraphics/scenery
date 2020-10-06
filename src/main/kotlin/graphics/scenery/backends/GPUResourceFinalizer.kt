package graphics.scenery.backends

import graphics.scenery.utils.LazyLogger
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue

/**
 * Resource finalizer class for objects allocated on the GPU that might need
 * additional cleanup after getting picked up by the GC.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class GPUResourceFinalizer<T>(val resource: T, val type: Class<*>, val queue: ReferenceQueue<T>) : PhantomReference<T>(resource, queue) {
    private val logger by LazyLogger()

    /**
     * Finalizes the resources of [resource] with the finalizer for the specific [type] used.
     * The finalizer has to be registered before with [registerFinalizer].
     */
    fun finalizeResources() {
        logger.debug("Finalising resources of {}", resource)

        val finalizer = finalizers[type]
        if(finalizer == null) {
            logger.error("Could not find GPU resource finalizer for ${type.simpleName}")
        } else {
            finalizer.invoke(resource as Any)
        }
    }

    /**
     * Companion object for [GPUResourceFinalizer].
     */
    companion object {
        private var finalizers = HashMap<Class<*>, (Any) -> Unit>()

        /**
         * Registers a new [finalizer] for the [type] given.
         */
        @Synchronized @JvmStatic
        fun registerFinalizer(type: Class<*>, finalizer: (Any) -> Unit) {
            finalizers[type] = finalizer
        }

        /**
         * Removes the finalizer for [type].
         */
        @Synchronized @JvmStatic
        fun removeFinalizer(type: Class<*>) {
            finalizers.remove(type)
        }
    }
}
