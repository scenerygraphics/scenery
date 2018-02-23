package graphics.scenery

import graphics.scenery.utils.LazyLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Flexible settings store for scenery. Stores a hash map of <String, Any>,
 * which one can query for a specific setting and type then.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Settings(override var hub: Hub? = null) : Hubable {
    /** Hash map storing all the settings */
    private var settingsStore = ConcurrentHashMap<String, Any>()
    protected val logger by LazyLogger()

    /**
     * Query the settings store for a setting [name] and type T
     *
     * @param[name] The name of the setting
     * @return The setting as type T
     */
    fun <T> get(name: String): T {
        if(!settingsStore.containsKey(name)) {
            logger.warn("WARNING: Settings don't contain '$name'")
        }
        return settingsStore.get(name) as T
    }

    /**
     * Compatibility function for Java, see [get].
     *
     * @param[name] Name of the setting to fetch.
     * @param[type] Class of the setting to fetch.
     * @return The setting, if found.
     */
    fun <T> getProperty(name: String, type: Class<T>): T{
        if(!settingsStore.containsKey(name)) {
            logger.warn("WARNING: Settings don't contain '$name'")
        }
        return settingsStore.get(name) as T
    }

    /**
     * Add or replace a setting in the store. Will only allow replacement
     * if types of existing and new setting match.
     *
     * @param[name] Name of the setting.
     * @param[contents] Contents of the setting, can be anything.
     */
    fun set(name: String, contents: Any): Any {
        // protect against type change
        if(settingsStore.containsKey(name)) {
            val type: Class<*> = settingsStore.get(name)!!.javaClass
            if(type == contents.javaClass) {
                settingsStore[name] = contents
            } else {
                if(settingsStore[name] is Float && contents is Double) {
                    settingsStore[name] = contents.toFloat()
                } else if(settingsStore[name] is Int && contents is Float) {
                    settingsStore[name] = contents.toInt()
                } else if(settingsStore[name] is Int && contents is Double) {
                    settingsStore[name] = contents.toInt()
                }
                else {
                    logger.warn("Cannot cast $contents from ${contents.javaClass} to $type, $name will stay ${settingsStore[name]}")
                }
            }

            return settingsStore[name]!!
        } else {
            settingsStore.put(name, contents)
            return settingsStore[name]!!
        }
    }

    /**
     * Lists all settings currently stored
     */
    fun list(): String {
        return settingsStore.map { "${it.key}=${it.value} (${it.value.javaClass.simpleName})" }.sorted().joinToString("\n")
    }

    fun getAllSettings(): List<String> {
        return settingsStore.keys().toList()
    }
}
