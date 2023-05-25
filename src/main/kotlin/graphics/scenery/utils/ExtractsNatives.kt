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

    }

    /**
     * Cleans old temporary native libraries, e.g. all directories in the temporary directory,
     * which have "scenery-natives-tmp" in their name, and do not have a lock file present.
     */
    fun cleanTempFiles() {
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

    /**
     * Utility function to extract native libraries from a given JAR, store them in a
     * temporary directory and modify the JRE's library path such that it can find
     * these libraries.
     *
     * @param[paths] A list of JAR paths to extract natives from.
     * @param[replace] Whether or not the java.library.path should be replaced.
     */
    fun extractLibrariesFromJar(paths: List<String>, replace: Boolean = false, load: Boolean = false): String {
        // FIXME: Kotlin bug, revert to LazyLogger as soon as https://youtrack.jetbrains.com/issue/KT-19690 is fixed.
        // val logger by LazyLogger()
        val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

        val tmpDir = Files.createTempDirectory("scenery-natives-tmp").toFile()
        val lock = File(tmpDir, ".lock")
        lock.createNewFile()
        lock.deleteOnExit()

        cleanTempFiles()
        val files = ArrayList<String>()

        val nativeLibraryExtensions = hashMapOf(
            Platform.WINDOWS to listOf("dll"),
            Platform.LINUX to listOf("so"),
            Platform.MACOS to listOf("dylib", "jnilib"))

        logger.debug("Got back ${paths.joinToString(", ")}")
        paths.filter { it.lowercase().endsWith("jar") }.forEach {
            logger.debug("Extracting $it...")

            val jar = JarFile(it)
            val enumEntries = jar.entries()

            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                if(file.getName().substringAfterLast(".") !in nativeLibraryExtensions[getPlatform()]!!) {
                    continue
                }
                files.add(tmpDir.absolutePath + File.separator + file.getName())
                val f = File(files.last())

                // create directory, if needed
                if (file.isDirectory()) {
                    f.mkdir()
                    continue
                }

                val ins = jar.getInputStream(file)
                val baos = ByteArrayOutputStream()
                val fos = FileOutputStream(f)

                val buffer = ByteArray(1024)
                var len: Int = ins.read(buffer)

                while (len > -1) {
                    baos.write(buffer, 0, len)
                    len = ins.read(buffer)
                }

                baos.flush()
                fos.write(baos.toByteArray())

                if(getPlatform() == Platform.MACOS && file.name.substringAfterLast(".") == "jnilib") {
                    logger.debug("macOS: Making dylib copy of jnilib file for compatibility")
                    try {
                        f.copyTo(File(tmpDir.absolutePath + File.separator + file.name.substringBeforeLast(".") + ".dylib"), false)
                    } catch (e: IOException) {
                        logger.warn("Failed to create copy of ${file.name} to ${file.name.substringBeforeLast(".")}.dylib")
                    }
                }

                fos.close()
                baos.close()
                ins.close()
            }
        }

        if(load) {
            files.forEach { lib ->
                logger.debug("Loading native library $lib")
                System.load(lib)
            }
        }

        return tmpDir.absolutePath
    }

    /**
     * Utility function to search the current class path for JARs with native libraries
     *
     * @param[searchName] The string to match the JAR's name against
     * @param[hint] A file name to look for, for the ImageJ classpath hack
     * @return A list of JARs matching [searchName]
     */
    fun getNativeJars(searchName: String, hint: String = ""): List<String> {
        val res = Thread.currentThread().contextClassLoader.getResource(hint)

        if (res == null) {
            LoggerFactory.getLogger(this.javaClass.simpleName).error("Could not find JAR matching \"" + searchName + "\" with native libraries (${getPlatform()}, $hint).")
            return listOf()
        }

        var jar = res.path
        var pathOffset = 5

        if (getPlatform() == Platform.WINDOWS) {
            pathOffset = 6
        }

        jar = jar.substring(jar.indexOf("file:/") + pathOffset).substringBeforeLast("!")
        return jar.split(File.pathSeparator)
    }
}
