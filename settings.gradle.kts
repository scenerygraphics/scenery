
enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("bb", "3.2.1")
            alias("foo").to("group:artifact:0.0.1")
        }
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://raw.githubusercontent.com/kotlin-graphics/mary/master")
    }
}

plugins {
    id("sciJava.catalogs") version "30.0.0+57"
}

//dependencyResolutionManagement {
//    versionCatalogs {
//        create("scenery") {
////            alias("commons-lang3").to("org.apache.commons", "commons-lang3").version {
////                strictly("[3.8, 4.0[")
////                prefer("3.9")
////            }
//
//            alias("jeromq").to("org.zeromq:jeromq:0.4.3")
//            alias("kryo").to("com.esotericsoftware:kryo:5.0.3")
//            alias("jvrpn").to("graphics.scenery:jvrpn:1.2.0")
//            alias("ffmpeg").to("org.bytedeco:ffmpeg:4.2.1-1.5.2")
//            alias("reflections").to("org.reflections:reflections:0.9.12")
//        }
//        create("msgpack") {
//            val version = "0.8.20"
//            alias("core").to("org.msgpack:msgpack-core:$version")
//            alias("jackson").to("org.msgpack:jackson-dataformat-msgpack:$version")
//            bundle("all", listOf("core", "jackson"))
//        }
//    }
//}

rootProject.name = "scenery"

gradle.rootProject {
    group = "graphics.scenery"
    version = "0.7.0-beta-8-SNAPSHOT-00"
    description = "flexible scenegraphing and rendering for scientific visualisation"
}

includeBuild("platform")
