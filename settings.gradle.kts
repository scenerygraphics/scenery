
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://raw.githubusercontent.com/kotlin-graphics/mary/master")
    }
}

rootProject.name = "scenery"

gradle.rootProject {
    //    group = "scenery"
    version = "0.7.0-beta-8-SNAPSHOT-001"
    description = "flexible scenegraphing and rendering for scientific visualisation"
}