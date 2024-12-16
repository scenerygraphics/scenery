package scenery

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.accessors.runtime.addExternalModuleDependencyTo

// this is the same in both scenery and sciview

fun DependencyHandlerScope.implementation(dep: Provider<MinimalExternalModuleDependency>, native: String) = dep.get().apply {
    add("implementation", this)
    addExternalModuleDependencyTo(this@implementation, "runtimeOnly",
                                  module.group, module.name, versionConstraint.displayName, null,
                                  native, null, null)
}

fun DependencyHandlerScope.implementation(dep: String, native: String) {
    add("implementation", dep)
    val split = dep.split(':')
    addExternalModuleDependencyTo(this@implementation, "runtimeOnly",
                                  split[0], split[1], split.getOrNull(2), null,
                                  native, null, null)
}

fun DependencyHandlerScope.implementation(dep: Provider<MinimalExternalModuleDependency>, natives: Array<String>) = dep.get().apply {
    add("implementation", this)
    for (native in natives)
        addExternalModuleDependencyTo(this@implementation, "runtimeOnly",
                                      module.group, module.name, versionConstraint.displayName, null,
                                      native, null, null)
}

fun DependencyHandlerScope.implementation(dep: String, natives: Array<String>) {
    add("implementation", dep)
    val split = dep.split(':')
    for (native in natives)
        addExternalModuleDependencyTo(this@implementation, "runtimeOnly",
                                      split[0], split[1], split.getOrNull(2), null,
                                      native, null, null)
}

fun DependencyHandlerScope.api(dep: Provider<MinimalExternalModuleDependency>, native: String) = dep.get().apply {
    add("api", this)
    addExternalModuleDependencyTo(this@api, "runtimeOnly",
                                  module.group, module.name, versionConstraint.displayName, null,
                                  native, null, null)
}

fun DependencyHandlerScope.api(dep: String, native: String) {
    add("api", dep)
    val split = dep.split(':')
    addExternalModuleDependencyTo(this@api, "runtimeOnly",
                                  split[0], split[1], split.getOrNull(2), null,
                                  native, null, null)
}

fun DependencyHandlerScope.api(dep: Provider<MinimalExternalModuleDependency>, natives: Array<String>) = dep.get().apply {
    add("api", this)
    for (native in natives)
        addExternalModuleDependencyTo(this@api, "runtimeOnly",
                                      module.group, module.name, versionConstraint.displayName, null,
                                      native, null, null)
}

fun DependencyHandlerScope.api(dep: String, natives: Array<String>) {
    add("api", dep)
    val split = dep.split(':')
    for (native in natives)
        addExternalModuleDependencyTo(this@api, "runtimeOnly",
                                      split[0], split[1], split.getOrNull(2), null,
                                      native, null, null)
}

val lwjglNatives = arrayOf("natives-windows", "natives-linux-arm64", "natives-linux", "natives-macos", "natives-macos-arm64")
val ffmpegNatives = arrayOf("windows-x86_64", "linux-x86_64", "macosx-x86_64", "macosx-arm64")
