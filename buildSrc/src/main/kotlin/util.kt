import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.accessors.runtime.addExternalModuleDependencyTo
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.net.URL
import java.util.regex.Pattern

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

/**
 * com.github.scenerygraphics:vector:958f2e6
 * net.java.dev.jna:jna
 * net.java.dev.jna:jna-platform:${jna}
 */
fun DependencyHandlerScope.sci(dep_: String, native: String? = null, test: Boolean = false) {
    var dep = dep_
    val println = false
    //    println("sci($dep)")
    val deps = dep.split(':')
    var vers = ""
    if (deps.size == 3) {
        vers = deps[2]
        //        println("deps.size=3, vers=$vers")
        if (vers.startsWith('$')) { // we need to prepare for later extraction
            vers = vers.substring(1)
            dep = dep.substringBeforeLast(':') // remove tail
        } else { // complete, eg: com.github.scenerygraphics:vector:958f2e6
            if (test) {
                if (println) println("testImplementation(\"$dep\")")
                testImplementation(dep)
            } else {
                if (println) println("implementation(\"$dep\")")
                implementation(dep)
            }
            if (native != null) if (test) {
                if (println) println("testRuntimeOnly(${deps[0]}, ${deps[1]}, ${deps[2]}, classifier = $native)")
                testRuntimeOnly(deps[0], deps[1], deps[2], classifier = native)
            } else {
                if (println) println("runtimeOnly(${deps[0]}, ${deps[1]}, ${deps[2]}, classifier = $native)")
                runtimeOnly(deps[0], deps[1], deps[2], classifier = native)
            }
            return
        }
    } else {
        check(deps.size == 2)
        vers = deps[1]
    }

    // we need to extract the version
    val version = vers.version
    //    println("vers=$vers, version=$version")
    if (test) {
        if (println) println("testImplementation(\"$dep:$version\")")
        testImplementation("$dep:$version")
    } else {
        if (println) println("implementation(\"$dep:$version\")")
        implementation("$dep:$version")
    }
    if (native != null) if (test) {
        if (println) println("testRuntimeOnly(${dep.substringBefore(':')}, ${dep.substringAfter(':')}, classifier = $native)")
        testRuntimeOnly(dep.substringBefore(':'), dep.substringAfter(':'), classifier = native)
    } else {
        if (println) println("runtimeOnly(${dep.substringBefore(':')}, ${dep.substringAfter(':')}, classifier = $native)")
        runtimeOnly(dep.substringBefore(':'), dep.substringAfter(':'), classifier = native)
    }
}

fun DependencyHandlerScope.testSci(dep: String, native: String? = null) = sci(dep, native, test = true)

val String.version: String
    get() {
        //        println("$this.version")
        var matcher = Pattern.compile("<$this\\.version>(.*)</$this\\.version>").matcher(pomSci) // exact
        if (!matcher.find()) {
            //            println("generic")
            matcher = Pattern.compile("<$this.*\\.version>(.*)</$this.*\\.version>").matcher(pomSci) // generic
        }
        //        println(matcher)
        matcher.reset().find()
        val vers = matcher.group(1)
        //        println(vers)
        //        println(matcher)
        return when { // recursive, ie: ${jogl.version}
            vers.startsWith("\${") && vers.endsWith(".version}") -> vers.drop(2).dropLast(9).version
            else -> vers
        }
    }

fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? = add("implementation", dependencyNotation)

fun DependencyHandler.runtimeOnly(dependencyNotation: Any): Dependency? = add("runtimeOnly", dependencyNotation)

fun DependencyHandler.runtimeOnly(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null,
        dependencyConfiguration: Action<ExternalModuleDependency>? = null
): ExternalModuleDependency = addExternalModuleDependencyTo(
        this, "runtimeOnly", group, name, version, configuration, classifier, ext, dependencyConfiguration
)

fun DependencyHandler.testImplementation(dependencyNotation: Any): Dependency? =
        add("testImplementation", dependencyNotation)

fun DependencyHandler.testRuntimeOnly(dependencyNotation: Any): Dependency? = add("testRuntimeOnly", dependencyNotation)

fun DependencyHandler.testRuntimeOnly(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null,
        dependencyConfiguration: Action<ExternalModuleDependency>? = null
): ExternalModuleDependency = addExternalModuleDependencyTo(
        this, "testRuntimeOnly", group, name, version, configuration, classifier, ext, dependencyConfiguration
)