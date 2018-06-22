package graphics.scenery

import graphics.scenery.utils.LazyLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Flexible settings store for scenery. Stores a hash map of <String, Any>,
 * which one can query for a specific setting and type then.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Settings(override var hub: Hub? = null) : Hubable {
    private var settingsStore = ConcurrentHashMap<String, Any>()
    private val logger by LazyLogger()

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

        @Suppress("UNCHECKED_CAST")
        val s = settingsStore[name] as? T
        if(s != null) {
            return s
        } else {
            throw IllegalStateException("Cast of $name failed.")
        }
    }

    /**
     * Compatibility function for Java, see [get]. Returns the settings value for [name], if found.
     */
    fun <T> getProperty(name: String): T{
        if(!settingsStore.containsKey(name)) {
            logger.warn("WARNING: Settings don't contain '$name'")
        }

        @Suppress("UNCHECKED_CAST")
        val s = settingsStore[name] as? T
        if(s != null) {
            return s
        } else {
            throw IllegalStateException("Cast of $name failed.")
        }
    }

    /**
     * Add or replace a setting in the store. Will only allow replacement
     * if types of existing and new setting match.
     *
     * @param[name] Name of the setting.
     * @param[contents] Contents of the setting, can be anything.
     */
    fun set(name: String, contents: Any): Any {
        // protect against unintended type change
        var current = settingsStore[name]

        if (current != null) {
            val type: Class<*> = current.javaClass

            if (type != contents.javaClass) {
                logger.warn("Casting $name from ${type.simpleName} to ${contents.javaClass.simpleName}. Are you sure about this?")
            }

            when {
                type == contents.javaClass -> settingsStore[name] = contents
                current is Float && contents is Double -> settingsStore[name] = contents.toFloat()
                current is Int && contents is Float -> settingsStore[name] = contents.toInt()
                current is Int && contents is Double -> settingsStore[name] = contents.toInt()
                else -> {
                    logger.warn("Will not cast $contents from ${contents.javaClass} to $type, $name will stay ${settingsStore[name]}")
                    current = null
                }
            }
        } else {
            settingsStore[name] = contents
        }

        return current ?: contents
    }

    /**
     * Lists all settings currently stored as String.
     */
    fun list(): String {
        return settingsStore.map { "${it.key}=${it.value} (${it.value.javaClass.simpleName})" }.sorted().joinToString("\n")
    }

    /**
     * Return the names of all settings as a [List] of Strings.
     */
    fun getAllSettings(): List<String> {
        return settingsStore.keys().toList()
    }
}
