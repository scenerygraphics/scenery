
pluginManagement {
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
