
pluginManagement {
    val kotlinVersion: String by settings
    val dokkaVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
    }

    repositories {
        gradlePluginPortal()
    }
}

enableFeaturePreview("VERSION_CATALOGS")

rootProject.name = "scenery"

gradle.rootProject {
    group = "graphics.scenery"
    version = "0.7.0-beta-8-SNAPSHOT-00"
    description = "flexible scenegraphing and rendering for scientific visualisation"
}
