package graphics.scenery.utils;

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.jar.JarFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL


/**
 * Helper interface for classes which might need to extract native libraries.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface ExtractsNatives {
    enum class Platform {
        UNKNOWN, WINDOWS, LINUX, MACOS
    }

    companion object {
        private val logger by lazyLogger()
        /**
         * Returns the platform based on the os.name system property.
         */
        @JvmStatic fun getPlatform(): Platform {
            val os = System.getProperty("os.name").lowercase()

            return when {
                os.contains("win") -> Platform.WINDOWS
                os.contains("linux") -> Platform.LINUX
                os.contains("mac") -> Platform.MACOS
                else -> Platform.UNKNOWN
            }

        }

        /**
         * Downloads a [file] from a given GitHub [organization]'s [repository]'s [release],
         * and puts it into ~/.scenery. This function was written for M1 support for JHDF5.
         *
         * Returns the base path to use for native file loading.
         */
        internal fun nativesFromGithubRelease(
            organization: String,
            repository: String,
            release: String,
            file: String,
            archAndOS: String,
            library: String,
            ext: String
        ): String {
            val url = "https://github.com/$organization/$repository/releases/download/$release/$file"
            val output = File(System.getProperty("user.home") + "/.scenery/native/$file")
            if(!output.exists()) {
                output.parentFile.mkdirs()

                if (!output.exists()) {
                    val created = output.createNewFile()
                    val stream = URL(url).openStream()
                    val out = output.outputStream()
                    stream.copyTo(out)
                    out.close()
                    stream.close()
                }
            }

            val basepath = File(System.getProperty("user.home") + "/.scenery/native/")
            val target = File(System.getProperty("user.home") + "/.scenery/native/$library/$archAndOS/lib$library.$ext")
            target.parentFile.mkdirs()
            return if(target.exists()) {
                basepath.absolutePath
            } else {
                val jarFile = JarFile(output)
                val name = "native/$library/$archAndOS/lib$library.$ext"
                logger.info("name is $name")
                val entry = jarFile.getJarEntry("native/$library/$archAndOS/lib$library.$ext")
                val stream = target.outputStream().buffered()
                stream.write(jarFile.getInputStream(entry).buffered().readAllBytes())
                stream.close()
                jarFile.close()
                basepath.absolutePath
            }
        }

        /**
         * Utility function to extract native libraries from the classpath, and store them in a
         * temporary directory.
         *
         * @param[paths] A list of JAR paths to extract natives from.
         * @param[load] Whether or not to directly load the extracted libraries.
         */
        fun extractLibrariesFromClasspath(paths: List<String>, load: Boolean = false): String {
            val logger = LoggerFactory.getLogger(Companion::class.java.simpleName)

            if(paths.isEmpty()) {
                throw IllegalStateException("Empty path list handed to extractLibrariesFromClasspath()")
            }

            val tmpDir = Files.createTempDirectory("scenery-natives-tmp").toFile()
            val lock = File(tmpDir, ".lock")
            lock.createNewFile()
            lock.deleteOnExit()

            cleanTempFiles()

            paths.forEach { nativeLibrary ->
                val f = this::class.java.classLoader.getResourceAsStream(nativeLibrary)

                if(f == null) {
                    logger.warn("Could not find native library $nativeLibrary in classpath.")
                    return@forEach
                }

                val out = tmpDir.resolve(nativeLibrary)
                if(!out.exists()) {
                    val outputStream = out.outputStream()
                    f.copyTo(outputStream)
                    outputStream.close()
                    logger.info("Extracted native library $nativeLibrary to ${out.absolutePath}")
                }

                if(load) {
                    logger.info("Loading native library from ${out.absolutePath}")
                    System.load(out.absolutePath)
                }
            }

            return tmpDir.absolutePath
        }

        /**
         * Cleans old temporary native libraries, e.g. all directories in the temporary directory,
         * which have "scenery-natives-tmp" in their name, and do not have a lock file present.
         */
        private fun cleanTempFiles() {
            File(System.getProperty("java.io.tmpdir")).listFiles().forEach { file ->
                if (file.isDirectory && file.name.contains("scenery-natives-tmp")) {
                    val lock = File(file, ".lock")

                    // delete the temporary directory only if the lock does not exist
                    if (!lock.exists()) {
                        file.deleteRecursively()
                    }
                }
            }
        }
    }
}
