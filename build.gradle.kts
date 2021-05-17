import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sciJava.*
import java.net.URL

plugins {
    val ktVersion = "1.4.20"
    java
    kotlin("jvm") version ktVersion
    scenery.base
//    scenery.docs
    scenery.publish
    scenery.sign
    id("com.github.elect86.sciJava") version "0.0.4"
    id("org.jetbrains.dokka") version ktVersion
    jacoco
}

//sciJava.debug = true

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
    maven("https://maven.scijava.org/content/groups/public")
}

"kotlin"("1.4.21")
"ui-behaviour"("2.0.3")
//"bigvolumeviewer"("0.1.9")
"ffmpeg"("4.2.2-1.5.3")
"jackson-dataformat-msgpack"("0.8.20")
"jeromq"("0.4.3")
"jinput"("2.0.9")
"jocl"("2.0.2")
"jvrpn"("1.2.0")
"kotlinx-coroutines-core"("1.3.9")
"kryo"("5.0.3")
"lwjgl"("3.2.3")
"lwjgl3-awt"("0.1.7")
"msgpack-core"("0.8.20")
"classgraph"("4.8.86")
"spirvcrossj"("0.8.0-1.1.106.0")
"reflections"("0.9.12")
"art-dtrack-sdk"("2.6.0")

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-M1")

    listOf("windows-amd64", "linux-i586", "linux-amd64", "macosx-universal").forEach {
        sciJava("org.jogamp.gluegen:gluegen-rt:2.3.2", "natives-$it") // this is crap, but will be polished eventually
        sciJava("org.jogamp.jogl:jogl-all:2.3.2", "natives-$it")
    }
    sciJava("org.slf4j:slf4j-api")
    sciJava("net.clearvolume:cleargl")
    sciJava("org.joml")
    sciJava("net.java.jinput:jinput:2.0.9", native = "natives-all")
    sciJava("org.scijava"["scijava-common", "script-editor", "ui-behaviour", "scripting-javascript", "scripting-jython"])
    sciJava("net.sf.trove4j")
    sciJava("net.java.dev.jna")
    sciJava("net.java.dev.jna:jna-platform")
    sciJava("org.jocl")
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        implementation("org.lwjgl:lwjgl$it")
        if (it != "-vulkan")
            runtimeOnlylwjglNatives("org.lwjgl", "lwjgl$it", version = versions["lwjgl"]) // "
    }
    sciJava("com.fasterxml.jackson.core:jackson-databind")
    sciJava("com.fasterxml.jackson.module:jackson-module-kotlin:\$jackson-databind")
    sciJava("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:\$jackson-databind")
    sciJava("graphics.scenery:spirvcrossj")
    runtimeOnlylwjglNatives("graphics.scenery", "spirvcrossj", version = versions["spirvcrossj"]) // "
    sciJava("org.zeromq:jeromq")
    sciJava("com.esotericsoftware:kryo")
    sciJava("org.msgpack:msgpack-core")
    sciJava("org.msgpack:jackson-dataformat-msgpack")
    sciJava("graphics.scenery:jvrpn")
    runtimeOnlylwjglNatives("graphics.scenery", "jvrpn", version = versions["jvrpn"]) // "
    //    runtimeOnly("graphics.scenery", "jvrpn", classifier = "natives-linux")
    //    runtimeOnly("graphics.scenery", "jvrpn", classifier = "natives-macos")
    sciJava("io.scif:scifio")
    sciJava("org.bytedeco:ffmpeg")
    listOf("windows", "linux", "macosx").forEach {
        runtimeOnly("org.bytedeco", "ffmpeg", classifier = "$it-x86_64", version = versions["ffmpeg"]) // "
    }
    sciJava("org.reflections")
    sciJava("io.github.classgraph")
    //TODO revert to official BVV
    api("sc.fiji:bigdataviewer-core:10.1.1-SNAPSHOT")
    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-26-SNAPSHOT")
    api("com.github.skalarproduktraum:jogl-minimal:1c86442")
    //sciJava("sc.fiji:bigvolumeviewer")
    //    sciJava("org.lwjglx:lwjgl3-awt")
    implementation("com.github.LWJGLX:lwjgl3-awt:cfd741a6")
    sciJava("org.janelia.saalfeldlab:n5"["", "-imglib2"])
    listOf("core", "structure", "modfinder").forEach {
        sciJava("org.biojava:biojava-$it:5.4.0") {
            exclude("org.slf4j", "slf4j-api")
            exclude("org.slf4j", "slf4j-simple")
            exclude("org.apache.logging.log4j", "log4j-slf4j-impl")
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.4.21")
    implementation("graphics.scenery:art-dtrack-sdk:2.6.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

    testSciJava("junit:junit")
    testSciJava("org.slf4j:slf4j-simple")
    testSciJava("net.imagej")
    testSciJava("net.imagej:ij")
    testSciJava("net.imglib2:imglib2-ij")
}

fun DependencyHandlerScope.runtimeOnlylwjglNatives(group: String, name: String, version: String? = null) =
    listOf("windows", "linux", "macos").forEach { runtimeOnly(group, name, classifier = "natives-$it", version = version) }

tasks {

    withType<KotlinCompile>().all {
        val version = System.getProperty("java.version").substringBefore('.').toInt()
        val default = if (version == 1) "1.8" else "$version"
        kotlinOptions {
            jvmTarget = project.properties["jvmTarget"]?.toString() ?: default
            freeCompilerArgs += listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        }
        sourceCompatibility = project.properties["sourceCompatibility"]?.toString() ?: default
    }

    dokkaHtml {
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/scenerygraphics/scenery/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.get().outputDirectory.get())
    archiveClassifier.set("javadoc")
}

val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.get().outputDirectory.get())
    archiveClassifier.set("html-doc")
}

artifacts {
    archives(dokkaJavadocJar)
    archives(dokkaHtmlJar)
}

java.withSourcesJar()
