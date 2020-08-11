package graphics.scenery.utils

import org.slf4j.LoggerFactory

/**
 * Contains methods to access and manipulate logback framework dynamically at run-time. Here 'dynamically' means without
 * referencing the logback JAR, but using it if found in the classpath.
 *
 * @author Muhammed Demirbas
 * @author Ulrik Guenther
 * @since 19 Mar 2013
 */
class LogbackUtils {

    companion object {
        val LOGBACK_CLASSIC = "ch.qos.logback.classic"
        val LOGBACK_CLASSIC_LOGGER = "ch.qos.logback.classic.Logger"
        val LOGBACK_CLASSIC_LEVEL = "ch.qos.logback.classic.Level"
        private val logger = LoggerFactory.getLogger(LogbackUtils::class.java)

        /**
         * Dynamically sets the logback log level for the given class to the specified level.
         *
         * @param loggerName Name of the logger to set its log level. If blank, root logger will be used.
         * @param logLevel   One of the supported log levels: TRACE, DEBUG, INFO, WARN, ERROR, FATAL,
         * OFF. `null` value is considered as 'OFF'.
         */
        @JvmStatic fun setLogLevel(name: String?, logLevel: String?): Boolean {
            var loggerName = name
            val logLevelUpper = logLevel?.toUpperCase() ?: "OFF"

            try {
                // TODO: Check deprecation warning again, esp with respect to JPMS
                @Suppress("DEPRECATION")
                val logbackPackage = Package.getPackage(LOGBACK_CLASSIC)
                if (logbackPackage == null) {
                    logger.info("Logback is not in the classpath!")
                    return false
                }

                // Use ROOT logger if given logger name is blank.
                if (loggerName == null || loggerName.trim { it <= ' ' }.isEmpty()) {
                    loggerName = getFieldValue(LOGBACK_CLASSIC_LOGGER, "ROOT_LOGGER_NAME") as String
                }

                // Obtain logger by the name
                val loggerObtained = LoggerFactory.getLogger(loggerName)
                if (loggerObtained == null) {
                    // I don't know if this case occurs
                    logger.warn("No logger for the name: {}", loggerName)
                    return false
                }

                val logLevelObj = getFieldValue(LOGBACK_CLASSIC_LEVEL, logLevelUpper)
                if (logLevelObj == null) {
                    logger.warn("No such log level: {}", logLevelUpper)
                    return false
                }

                val paramTypes = arrayOf<Class<*>>(logLevelObj.javaClass)
                val params = arrayOf(logLevelObj)

                val clz = Class.forName(LOGBACK_CLASSIC_LOGGER)
                val method = clz.getMethod("setLevel", *paramTypes)
                method.invoke(loggerObtained, *params)

                logger.debug("Log level set to {} for the logger '{}'", logLevelUpper, loggerName)
                return true
            } catch (e: Exception) {
                logger.warn("Couldn't set log level to {} for the logger '{}'", logLevelUpper, loggerName, e)
                return false
            }

        }

        @JvmStatic private fun getFieldValue(fullClassName: String, fieldName: String): Any? {
            try {
                val clazz = Class.forName(fullClassName)
                val field = clazz.getField(fieldName)
                return field.get(null)
            } catch (ignored: ClassNotFoundException) {
                return null
            } catch (ignored: IllegalAccessException) {
                return null
            } catch (ignored: IllegalArgumentException) {
                return null
            } catch (ignored: NoSuchFieldException) {
                return null
            } catch (ignored: SecurityException) {
                return null
            }

        }
    }
}

