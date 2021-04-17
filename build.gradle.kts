import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import scenery.implementation
import sciJava.*
import sciJava.dsl.runtimeOnly
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
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://raw.githubusercontent.com/kotlin-graphics/mary/master")
    maven("https://jitpack.io")
}

"ui-behaviour"("2.0.3")
//"bigvolumeviewer"("0.1.9")
"kotlinx-coroutines-core"("1.3.9")
"lwjgl3-awt"("0.1.7")
"msgpack-core"("0.8.20")
"classgraph"("4.8.86")
"spirvcrossj"("0.7.1-1.1.106.0")
"reflections"("0.9.12")
"art-dtrack-sdk"("2.6.0")

dependencies {

    implementation(platform("sciJava:platform:30.0.0+6"))

    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-M1")

    implementation(jogamp.gluegen, joglNative)
    implementation(jogamp.jogl, joglNative)
    implementation(slf4j.api)
    implementation(misc.cleargl)
    implementation(misc.joml)
    sciJava("net.java.jinput:jinput:2.0.9", native = "natives-all")
    implementation("org.jocl:jocl:2.0.2")
    implementation(sciJava.common)
    implementation(sciJava.scriptEditor)
    implementation(sciJava.uiBehaviour)
    implementation(sciJava.scriptingJavascript)
    implementation(sciJava.scriptingJython)
    implementation(misc.trove)
    implementation(jna.bundles.all)
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        implementation("org.lwjgl:lwjgl$it")
        if (it != "-vulkan")
            runtimeOnlylwjglNatives("org.lwjgl", "lwjgl$it", version = versions["lwjgl"]) // "
    }
    implementation(jackson.bundles.all)
    implementation("graphics.scenery:spirvcrossj:0.7.1-1.1.106.0", lwjglNatives)
    implementation("org.zeromq:jeromq:0.4.3")
    implementation("com.esotericsoftware:kryo:5.0.3")
    implementation("org.msgpack:msgpack-core:0.8.20")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.8.20")
    implementation("graphics.scenery:jvrpn:1.2.0", lwjglNatives)
    implementation(scifio.scifio)
    implementation("org.bytedeco:ffmpeg:4.2.1-1.5.2", ffmpegNatives)
    implementation("org.reflections:reflections:0.9.12")
    implementation("io.github.classgraph:classgraph:4.8.86")
    //TODO revert to official BVV
    api("sc.fiji:bigdataviewer-core:10.1.1-SNAPSHOT")
    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-26-SNAPSHOT")
    api("com.github.skalarproduktraum:jogl-minimal:1c86442")
    //sciJava("sc.fiji:bigvolumeviewer")
    //    sciJava("org.lwjglx:lwjgl3-awt")
    implementation("com.github.LWJGLX:lwjgl3-awt:cfd741a6")
    implementation(n5.core)
    implementation(n5.imglib2)
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

    testImplementation(misc.junit4)
    testImplementation(slf4j.simple)
    testImplementation(imagej.core)
    testImplementation(imagej.ij)
    testImplementation(imgLib2.ij)
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
