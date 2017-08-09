package graphics.scenery.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

/**
 * Loggable - interface to flexibly return correct logger names for classes implementing this interface
 *
 * Heavily inspired by [Idiomatic way of logging in Kotlin](https://stackoverflow.com/questions/34416869/idiomatic-way-of-logging-in-kotlin).
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */

private fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> {
    return if (ofClass.enclosingClass != null && ofClass.enclosingClass.kotlin.companionObject?.java == ofClass) {
        ofClass.enclosingClass
    } else {
        ofClass
    }
}

fun <R : Any> R.LazyLogger(): Lazy<Logger> {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", System.getProperty("scenery.LogLevel", "info"))
    return lazyOf(LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).simpleName))
}
