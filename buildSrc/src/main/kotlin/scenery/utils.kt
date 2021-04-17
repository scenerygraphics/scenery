package scenery

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.accessors.runtime.addExternalModuleDependencyTo

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
                                  split[0], split[1], split.getOrNull(3), null,
                                  native, null, null)
}