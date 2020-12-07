package scenery

import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.net.URL
import java.util.regex.Pattern

/**
 * com.github.scenerygraphics:vector:958f2e6
 * net.java.dev.jna:jna
 * net.java.dev.jna:jna-platform:${jna}
 */
fun DependencyHandlerScope.sci(dep_: String, native: String? = null, test: Boolean = false,
                               config: Action<ExternalModuleDependency>? = null) {
    var dep = "$prefix$dep_"
    val println = false //dep.startsWith("net.imagej:imagej-mesh")
    //    println("sci($dep)")
    val deps = dep.split(':')
    var vers: String
    if (deps.size == 3) {
        vers = deps[2]
        //        println("deps.size=3, vers=$vers")
        if (vers.startsWith('$')) { // we need to prepare for later extraction
            vers = vers.substring(1)
            dep = dep.substringBeforeLast(':') // remove tail
        } else { // complete, eg: com.github.scenerygraphics:vector:958f2e6
            if (test) {
                if (println) println("testImplementation(\"$dep\")")
                when (config) {
                    null -> testImplementation(dep)
                    else -> testImplementation(dep, config)
                }
            } else {
                if (println) println("implementation(\"$dep\")")
                when (config) {
                    null -> implementation(dep)
                    else -> implementation(dep, config)
                }
            }
            if (native != null)
                if (test) {
                    if (println) println("testRuntimeOnly(${deps[0]}, ${deps[1]}, ${deps[2]}, classifier = $native)")
                    testRuntimeOnly(deps[0], deps[1], deps[2], classifier = native, dependencyConfiguration = config)
                } else {
                    if (println) println("runtimeOnly(${deps[0]}, ${deps[1]}, ${deps[2]}, classifier = $native)")
                    runtimeOnly(deps[0], deps[1], deps[2], classifier = native, dependencyConfiguration = config)
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
        when (config) {
            null -> testImplementation("$dep:$version")
            else -> testImplementation("$dep:$version", config)
        }
    } else {
        if (println) println("implementation(\"$dep:$version\")")
        when (config) {
            null -> implementation("$dep:$version")
            else -> implementation("$dep:$version", config)
        }
    }
    if (native != null)
        sciRuntimeOnly(dep, native, test, config)
}

fun DependencyHandlerScope.sciRuntimeOnly(dep_: String, classifier: String? = null, test: Boolean = false,
                                          config: Action<ExternalModuleDependency>? = null) {
    val dep = "$prefix$dep_"
    val group = dep.substringBefore(':')
    val name = dep.substringAfter(':')
    val println = false
    if (test) {
        if (println) println("testRuntimeOnly($group, $name, classifier = $classifier)")
        testRuntimeOnly(group, name, classifier = classifier, dependencyConfiguration = config)
    } else {
        if (println) println("runtimeOnly($group, $name, classifier = $classifier)")
        runtimeOnly(group, name, classifier = classifier, dependencyConfiguration = config)
    }
}

fun DependencyHandlerScope.testSci(dep: String, native: String? = null, config: Action<ExternalModuleDependency>? = null) =
        sci(dep, native, test = true, config = config)

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