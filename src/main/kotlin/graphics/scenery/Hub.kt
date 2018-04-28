package graphics.scenery

import graphics.scenery.backends.Display
import graphics.scenery.controls.TrackerInput
import java.util.concurrent.ConcurrentHashMap

/**
 * The Hub class interconnects the different components a scenery application may
 * have, such as a renderer or compute context (see also [SceneryElement].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Hub(val name: String = "default") {
    /** Hash map storage for all the [SceneryElements] and their instances */
    val elements: ConcurrentHashMap<SceneryElement, Any> = ConcurrentHashMap()

    init {
        if(getHub(name) != null) {
            throw IllegalStateException("Can't have multiple default hubs")
        }

        addHub(name, this)
    }

    /**
     * Adds a given [SceneryElement] to the Hub.
     *
     * @param[type] The type of [SceneryElement] to add.
     * @param[obj] The [Hubable] object.
     */
    fun add(type: SceneryElement, obj: Hubable) {
        elements.put(type, obj)

        obj.hub = this
    }

    fun addApplication(application: SceneryBase) {
        elements.put(SceneryElement.Application, application)
    }

    fun getApplication(): SceneryBase? {
        return elements.get(SceneryElement.Application) as? SceneryBase
    }

    /**
     * Query the Hub for a given type of [SceneryElement]
     *
     * @param[type] [SceneryElement] type.
     * @return The instance of [SceneryElement] currently registered.
     */
    fun get(type: SceneryElement): Any? {
        return elements.get(type)
    }

    fun <T: Hubable> get(type: SceneryElement): T? {
        if(elements.containsKey(type)) {
            return elements.get(type) as T
        } else {
            return null
        }
    }

    /**
     * Check whether the Hub has this type of [SceneryElement] registered
     *
     * @param[type] [SceneryElement] type to query for.
     * @return True if [type] is registered, else false.
     */
    fun has(type: SceneryElement): Boolean {
        return elements.containsKey(type)
    }

    fun getWorkingHMD(): TrackerInput? {
        if (this.has(SceneryElement.HMDInput)
            && (this.get(SceneryElement.HMDInput) as TrackerInput).initializedAndWorking()) {
            return this.get(SceneryElement.HMDInput) as TrackerInput
        } else {
            return null
        }
    }

    fun getWorkingHMDDisplay(): Display? {
        if (this.has(SceneryElement.HMDInput)
            && (this.get(SceneryElement.HMDInput) as Display).initializedAndWorking()) {
            return this.get(SceneryElement.HMDInput) as Display
        } else {
            return null
        }
    }

    companion object {
        private val hubs = ConcurrentHashMap<String, Hub>()

        fun addHub(name: String, hub: Hub): Hub {
            hubs[name] = hub
            return hub
        }

        fun getHub(name: String): Hub? {
            return hubs[name]
        }

        fun getDefaultHub(): Hub {
            return hubs["default"]!!
        }

        internal fun cleanHubs() {
            hubs.clear()
        }
    }
}
