import org.gradle.kotlin.dsl.api
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import scenery.*
import java.net.URL
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val ktVersion = "1.5.0"
    java
    kotlin("jvm") version ktVersion
    scenery.base
    //    scenery.docs
    scenery.publish
    scenery.sign
    id("org.jetbrains.dokka") version "1.4.30"
    jacoco
    //    id("sciJava.platform") version "30.0.0+15" // workaround for jitpack issue
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://raw.githubusercontent.com/kotlin-graphics/mary/master")
    maven("https://jitpack.io")
}

dependencies {
    // we cant use a platform because of a jitpack issue, we apply the dependencies constraints via
    // the sciJava.platform plugin above in the `plugins {}` scope
    //    implementation(platform("sciJava:platform:30.0.0+6"))
    implementation(platform("org.scijava:pom-scijava:31.1.0"))

    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

    implementation("org.jogamp.gluegen:gluegen-rt-main")
    joglNatives.forEach { runtimeOnly("org.jogamp.gluegen", "gluegen-rt", classifier = it) }
    implementation("org.jogamp.jogl:jogl-all-main")
    joglNatives.forEach { runtimeOnly("org.jogamp.jogl", "jogl-all", classifier = it) }
    implementation("org.slf4j:slf4j-api")
    implementation("net.clearvolume:cleargl")
    implementation("org.joml:joml")
    implementation("net.java.jinput:jinput:2.0.9")
    runtimeOnly("net.java.jinput", "jinput", "2.0.9", classifier = "natives-all")
    implementation("org.jocl:jocl:2.0.2")
    implementation("org.scijava:scijava-common")
    implementation("org.scijava:script-editor")
    implementation("org.scijava:ui-behaviour")
    implementation("org.scijava:scripting-javascript")
    implementation("org.scijava:scripting-jython")
    implementation("net.sf.trove4j:trove4j")
    implementation("net.java.dev.jna:jna")
        implementation("net.java.dev.jna:jna-platform:4.5.2") // TODO align this to ${jna.platform}
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        if (it == "-vulkan")
            api("org.lwjgl:lwjgl$it")
        else
            api("org.lwjgl:lwjgl$it", lwjglNatives)
    }
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("graphics.scenery:spirvcrossj:0.8.0-1.1.106.0", lwjglNatives)
    implementation("org.zeromq:jeromq:0.4.3")
    implementation("com.esotericsoftware:kryo:5.1.1")
    implementation("de.javakaffee:kryo-serializers:0.45")
    implementation("org.msgpack:msgpack-core:0.8.20")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.8.20")
    api("graphics.scenery:jvrpn:1.2.0", lwjglNatives)
    implementation("io.scif:scifio")
    implementation("org.bytedeco:ffmpeg:4.2.1-1.5.2", ffmpegNatives)
    implementation("org.reflections:reflections:0.9.12")
    implementation("io.github.classgraph:classgraph:4.8.86")

    api("sc.fiji:bigdataviewer-core:10.2.0")
    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-28")

    //TODO revert to official BVV
    api("com.github.skalarproduktraum:jogl-minimal:1c86442")

    implementation("com.github.LWJGLX:lwjgl3-awt:cfd741a6")
    implementation("org.janelia.saalfeldlab:n5")
    implementation("org.janelia.saalfeldlab:n5-imglib2")
    listOf("core", "structure", "modfinder").forEach {
        implementation("org.biojava:biojava-$it:5.4.0") {
            exclude("org.slf4j", "slf4j-api")
            exclude("org.slf4j", "slf4j-simple")
            exclude("org.apache.logging.log4j", "log4j-slf4j-impl")
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.5.0")
    implementation("graphics.scenery:art-dtrack-sdk:2.6.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

    testImplementation("junit:junit")
    testImplementation("org.slf4j:slf4j-simple")
    testImplementation("net.imagej:imagej")
    testImplementation("net.imagej:ij")
    testImplementation("net.imglib2:imglib2-ij")
}

val isRelease: Boolean
    get() = System.getProperty("release") == "true"

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
        enabled = isRelease
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/scenerygraphics/scenery/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }

    named<ShadowJar>("shadowJar") {
        isZip64 = true
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
    if (isRelease) {
        archives(dokkaJavadocJar)
        archives(dokkaHtmlJar)
    }
}

jacoco {
    toolVersion = "0.8.7"
}

java.withSourcesJar()

// disable Gradle metadata file creation on Jitpack, as jitpack modifies
// the metadata file, resulting in broken metadata with missing native dependencies.
if (System.getenv("JITPACK") != null) {
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
}

