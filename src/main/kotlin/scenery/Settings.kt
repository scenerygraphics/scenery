package scenery

import java.util.concurrent.ConcurrentHashMap

/**
 * <Description>
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
open class Settings {
    var settingsStore = ConcurrentHashMap<String, Any>()

    inline fun <reified T> get(name: String): T {
        if(!settingsStore.containsKey(name)) {
            System.err.println("WARNING: Settings don't contain '$name'")
        }
        return settingsStore.get(name) as T
    }

    fun <T> getProperty(name: String, type: Class<T>): T{
        if(!settingsStore.containsKey(name)) {
            System.err.println("WARNING: Settings don't contain '$name'")
        }
        return settingsStore.get(name) as T
    }

    fun set(name: String, contents: Any) {
        // protect against type change
        if(settingsStore.containsKey(name)) {
            val type: Class<*> = settingsStore.get(name)!!.javaClass
            settingsStore[name] = contents
        } else {
            settingsStore.put(name, contents)
        }
    }
}