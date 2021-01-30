package scenery

//import org.gradle.api.Action
//import org.gradle.api.artifacts.*
//import org.gradle.api.artifacts.dsl.ArtifactHandler
//import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
//import org.gradle.api.artifacts.dsl.DependencyHandler
//import org.gradle.kotlin.dsl.accessors.runtime.addDependencyTo
//import org.gradle.kotlin.dsl.accessors.runtime.addExternalModuleDependencyTo
//import org.gradle.kotlin.dsl.add
//import org.gradle.kotlin.dsl.create
//
//
///**
// * Adds a dependency to the 'testImplementation' configuration.
// *
// * @param dependencyNotation notation for the dependency to be added.
// * @return The dependency.
// *
// * @see [DependencyHandler.add]
// */
//fun DependencyHandler.testImplementation(dependencyNotation: Any): Dependency? =
//        add("testImplementation", dependencyNotation)
//
///**
// * Adds a dependency to the 'testImplementation' configuration.
// *
// * @param dependencyNotation notation for the dependency to be added.
// * @param dependencyConfiguration expression to use to configure the dependency.
// * @return The dependency.
// *
// * @see [DependencyHandler.add]
// */
//fun DependencyHandler.testImplementation(dependencyNotation: String,
//                                         dependencyConfiguration: Action<ExternalModuleDependency>): ExternalModuleDependency =
//        addDependencyTo(this, "testImplementation", dependencyNotation, dependencyConfiguration)
//
///**
// * Adds a dependency to the 'testImplementation' configuration.
// *
// * @param group the group of the module to be added as a dependency.
// * @param name the name of the module to be added as a dependency.
// * @param version the optional version of the module to be added as a dependency.
// * @param configuration the optional configuration of the module to be added as a dependency.
// * @param classifier the optional classifier of the module artifact to be added as a dependency.
// * @param ext the optional extension of the module artifact to be added as a dependency.
// * @param dependencyConfiguration expression to use to configure the dependency.
// * @return The dependency.
// *
// * @see [DependencyHandler.create]
// * @see [DependencyHandler.add]
// */
//fun DependencyHandler.testImplementation(group: String,
//                                         name: String,
//                                         version: String? = null,
//                                         configuration: String? = null,
//                                         classifier: String? = null,
//                                         ext: String? = null,
//                                         dependencyConfiguration: Action<ExternalModuleDependency>? = null): ExternalModuleDependency =
//        addExternalModuleDependencyTo(this, "testImplementation", group, name, version, configuration, classifier, ext, dependencyConfiguration)
//
///**
// * Adds a dependency to the 'testImplementation' configuration.
// *
// * @param dependency dependency to be added.
// * @param dependencyConfiguration expression to use to configure the dependency.
// * @return The dependency.
// *
// * @see [DependencyHandler.add]
// */
//fun <T : ModuleDependency> DependencyHandler.testImplementation(dependency: T,
//                                                                dependencyConfiguration: T.() -> Unit): T =
//        add("testImplementation", dependency, dependencyConfiguration)
//
///**
// * Adds a dependency constraint to the 'testImplementation' configuration.
// *
// * @param constraintNotation the dependency constraint notation
// *
// * @return the added dependency constraint
// *
// * @see [DependencyConstraintHandler.add]
// */
//fun DependencyConstraintHandler.testImplementation(constraintNotation: Any): DependencyConstraint? =
//        add("testImplementation", constraintNotation)
//
///**
// * Adds a dependency constraint to the 'testImplementation' configuration.
// *
// * @param constraintNotation the dependency constraint notation
// * @param block the block to use to configure the dependency constraint
// *
// * @return the added dependency constraint
// *
// * @see [DependencyConstraintHandler.add]
// */
//fun DependencyConstraintHandler.testImplementation(constraintNotation: Any, block: DependencyConstraint.() -> Unit): DependencyConstraint? =
//        add("testImplementation", constraintNotation, block)
//
///**
// * Adds an artifact to the 'testImplementation' configuration.
// *
// * @param artifactNotation the group of the module to be added as a dependency.
// * @return The artifact.
// *
// * @see [ArtifactHandler.add]
// */
//fun ArtifactHandler.testImplementation(artifactNotation: Any): PublishArtifact =
//        add("testImplementation", artifactNotation)
//
///**
// * Adds an artifact to the 'testImplementation' configuration.
// *
// * @param artifactNotation the group of the module to be added as a dependency.
// * @param configureAction The action to execute to configure the artifact.
// * @return The artifact.
// *
// * @see [ArtifactHandler.add]
// */
//fun ArtifactHandler.testImplementation(artifactNotation: Any,
//                                       configureAction: ConfigurablePublishArtifact.() -> Unit): PublishArtifact =
//        add("testImplementation", artifactNotation, configureAction)
//


