package graphics.scenery.utils;

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.jar.JarFile

/**
 * Helper interface for classes which might need to extract native libraries.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface ExtractsNatives {
    enum class Platform {
        UNKNOWN, WINDOWS, LINUX, MACOS
    }

    /**
     * Returns the platform based on the os.name system property.
     */
    fun getPlatform(): Platform {
        val os = System.getProperty("os.name").toLowerCase()

        if (os.contains("win")) {
            return Platform.WINDOWS
        } else if (os.contains("linux")) {
            return Platform.LINUX
        } else if (os.contains("mac")) {
            return Platform.MACOS
        }

        return Platform.UNKNOWN
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
    fun extractLibrariesFromJar(paths: List<String>, replace: Boolean = false) {
        // FIXME: Kotlin bug, revert to LazyLogger as soon as https://youtrack.jetbrains.com/issue/KT-19690 is fixed.
        // val logger by LazyLogger()
        val logger = LoggerFactory.getLogger(this.javaClass.simpleName)

        val lp = System.getProperty("java.library.path")
        val tmpDir = Files.createTempDirectory("scenery-natives-tmp").toFile()
        val lock = File(tmpDir, ".lock")
        lock.createNewFile()
        lock.deleteOnExit()

        cleanTempFiles()

        logger.debug("Got back ${paths.joinToString(", ")}")
        paths.filter { it.toLowerCase().endsWith("jar") }.forEach {
            logger.debug("Extracting $it...")

            val jar = JarFile(it)
            val enumEntries = jar.entries()

            while (enumEntries.hasMoreElements()) {
                val file = enumEntries.nextElement()
                val f = File(tmpDir.absolutePath + File.separator + file.getName())

                // create directory, if needed
                if (file.isDirectory()) {
                    f.mkdir()
                    continue
                }

                val ins = jar.getInputStream(file)
                val fos = FileOutputStream(f)
                while (ins.available() > 0) {
                    fos.write(ins.read())
                }

                fos.close()
                ins.close()
            }
        }

        if (replace) {
            System.setProperty("java.library.path", paths.joinToString(File.pathSeparator))
        } else {
            val newPath = "${lp}${File.pathSeparator}${tmpDir.absolutePath}"
            logger.debug("New java.library.path is $newPath")
            System.setProperty("java.library.path", newPath)
        }

        val fieldSysPath = ClassLoader::class.java.getDeclaredField("sys_paths")
        fieldSysPath.setAccessible(true)
        fieldSysPath.set(null, null)

        logger.debug("java.library.path is now ${System.getProperty("java.library.path")}")
    }

    /**
     * Utility function to search the current class path for JARs with native libraries
     *
     * @param[searchName] The string to match the JAR's name against
     * @param[hint] A file name to look for, for the ImageJ classpath hack
     * @return A list of JARs matching [searchName]
     */
    fun getNativeJars(searchName: String, hint: String = ""): List<String> {
        val classpath = System.getProperty("java.class.path")

        if (classpath.toLowerCase().contains("imagej-launcher")) {
            val res = Thread.currentThread().contextClassLoader.getResource(hint)

            if (res == null) {
                LoggerFactory.getLogger(this.javaClass.simpleName).error("Could not find JAR with native libraries.")
                return listOf()
            }

            var jar = res.path
            var pathOffset = 5

            if(getPlatform() == Platform.WINDOWS) {
                pathOffset = 6
            }

            jar = jar.substring(jar.indexOf("file:/") + pathOffset).substringBeforeLast("!")
            return jar.split(File.pathSeparator)
        } else {
            return classpath.split(File.pathSeparator).filter { it.contains(searchName) }
        }
    }
}
