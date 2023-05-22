package graphics.scenery

import graphics.scenery.utils.lazyLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Flexible settings store for scenery. Stores a hash map of <String, Any>,
 * which one can query for a specific setting and type then.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class Settings(override var hub: Hub? = null) : Hubable {
    private var settingsStore = ConcurrentHashMap<String, Any>()
    private val logger by lazyLogger()

    init {
        val properties = System.getProperties()
        properties.forEach { p ->
            val key = p.key as? String ?: return@forEach
            val value = p.value as? String ?: return@forEach

            if(!key.startsWith("scenery.")) {
                return@forEach
            }

            val setting = when {
                value.lowercase() == "false" || value.lowercase() == "true" -> value.toBoolean()
                value.lowercase().contains("f") && value.lowercase().replace("f", "").toFloatOrNull() != null -> value.lowercase().replace("f", "").toFloat()
                value.lowercase().contains("l") && value.lowercase().replace("l", "").toLongOrNull() != null -> value.lowercase().replace("l", "").toLong()
                value.toIntOrNull() != null -> value.toInt()
                else -> value
            }

            set(key.substringAfter("scenery."), setting)
        }
    }

    /**
     * Query the settings store for a setting [name] and type T
     *
     * @param[name] The name of the setting
     * @return The setting as type T
     */
    fun <T> get(name: String, default: T? = null): T {
        if(!settingsStore.containsKey(name)) {
            if(default == null) {
                logger.warn("Settings don't contain '$name'")
            } else {
                logger.debug("Settings don't contain '$name'")
            }
        }

        @Suppress("UNCHECKED_CAST")
        val s = settingsStore[name] as? T
        return s
            ?: (default ?: throw IllegalStateException("Cast of $name failed, the setting might not exist (current value: $s)"))
    }

    /**
     * Compatibility function for Java, see [get]. Returns the settings value for [name], if found.
     */
    @JvmOverloads fun <T> getProperty(name: String, default: T? = null): T{
        if(!settingsStore.containsKey(name)) {
            if(default == null) {
                logger.warn("Settings don't contain '$name'")
            } else {
                logger.debug("Settings don't contain '$name'")
            }
        }

        @Suppress("UNCHECKED_CAST")
        val s = settingsStore[name] as? T
        return s
            ?: (default ?: throw IllegalStateException("Cast of $name failed, the setting might not exist (current value: $s)"))
    }

    /**
     * Add or a setting in the store only if it does not exist yet.
     * Will only allow replacement if types of existing and new setting match.
     *
     * @param[name] Name of the setting.
     * @param[contents] Contents of the setting, can be anything.
     */
    fun setIfUnset(name: String, contents: Any): Any {
        return settingsStore[name] ?: set(name, contents)
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
