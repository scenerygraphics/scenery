package graphics.scenery.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * LazyLogger - Extension function to flexibly return correct logger names for classes implementing this interface
 *
 * Heavily inspired by [Idiomatic way of logging in Kotlin](https://stackoverflow.com/questions/34416869/idiomatic-way-of-logging-in-kotlin).
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
fun <R : Any> R.lazyLogger(logLevel: String? = null): Lazy<Logger> {
    if(logLevel != null) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel)
    }
    return lazyOf(LoggerFactory.getLogger(this::class.java.simpleName))
}
