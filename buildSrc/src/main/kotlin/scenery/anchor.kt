package scenery

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.net.URL

val pomSciVersion = "29.2.1"
val pomSci = URL("https://maven.scijava.org/content/groups/public/org/scijava/pom-scijava/$pomSciVersion/pom-scijava-$pomSciVersion.pom").readText()

val is64: Boolean = run {
    val arch = System.getenv("PROCESSOR_ARCHITECTURE")
    val wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432")
    arch?.endsWith("64") == true || wow64Arch?.endsWith("64") == true
}

val os = DefaultNativePlatform.getCurrentOperatingSystem()

val joglNative = "natives-" + when {
    os.isWindows -> "windows-amd64".also { check(is64) }
    os.isLinux -> "linux-" + if (is64) "i586" else "amd64"
    os.isMacOsX -> "macosx-universal"
    else -> error("invalid")
}

val lwjglNatives = "natives-" + when {
    os.isWindows -> "windows"
    os.isLinux -> "linux"
    os.isMacOsX -> "macos"
    else -> error("invalid")
}

val ffmpegNatives = when {
    os.isWindows -> "windows"
    os.isLinux -> "linux"
    os.isMacOsX -> "macosx"
    else -> error("invalid")
} + "-x86_64"