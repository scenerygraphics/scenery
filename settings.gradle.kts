pluginManagement {
    val kotlinVersion: String by settings
    val dokkaVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion

        id("io.github.goooler.shadow") version "8.1.7"
    }

    repositories {
        gradlePluginPortal()
    }
}

//buildscript {
//    val dokkaVersion: String by settings
//
//    dependencies {
//        classpath("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")
//    }
//
//    repositories {
//        mavenCentral()
//        gradlePluginPortal()
//    }
//}

rootProject.name = "scenery"

gradle.rootProject {
    group = "graphics.scenery"
    version = project.properties["version"]!!
    description = "flexible scenegraphing and rendering for scientific visualisation"
}
