import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.runtimeOnly
import org.gradle.kotlin.dsl.testImplementation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import scenery.*
import java.net.URL

plugins {
    java
    kotlin("jvm") version "1.4.10"
    scenery.publish
    scenery.sign
    id("org.jetbrains.dokka") version "1.4.10"
}

val ktVersion = "1.4.10"

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
    maven("https://maven.scijava.org/content/groups/public")
}

dependencies {

    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-M1")

    //    implementation(platform("org.scijava:pom-scijava:29.2.1"))
    //    implementation(platform("org.scijava:pom-scijava-base:11.2.0"))
    //    components.all<Rule>()

    sci("org.jogamp.gluegen:gluegen-rt", joglNative)
    sci("org.jogamp.jogl:jogl-all", joglNative)
    sci("org.slf4j:slf4j-api")
    sci("net.clearvolume:cleargl")
    sci("org.joml:joml")
    sci("com.github.scenerygraphics:vector:958f2e6")
    sci("net.java.jinput:jinput:2.0.9", native = "natives-all")
    listOf("scijava-common", "script-editor", /*"ui-behaviour", overwrite! */ "scripting-javascript", "scripting-jython").forEach {
        sci("org.scijava:$it")
    }
    sci("org.scijava:ui-behaviour:2.0.2")
    sci("net.sf.trove4j:trove4j")
    sci("net.java.dev.jna:jna")
    sci("net.java.dev.jna:jna-platform:\$jna")
    implementation("org.jocl:jocl:2.0.2")
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        implementation("org.lwjgl:lwjgl$it")
        if (it != "-vulkan")
            runtimeOnly("org.lwjgl", "lwjgl$it", classifier = lwjglNatives)
    }
    sci("com.fasterxml.jackson.core:jackson-databind")
    sci("com.fasterxml.jackson.module:jackson-module-kotlin:\$jackson")
    sci("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:\$jackson")
    implementation("graphics.scenery:spirvcrossj:0.7.0-1.1.106.0")
    runtimeOnly("graphics.scenery", "spirvcrossj", classifier = lwjglNatives)
    implementation("org.zeromq:jeromq:0.4.3")
    implementation("com.esotericsoftware:kryo:4.0.2")
    implementation("org.msgpack:msgpack-core:0.8.20")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.8.20")
    implementation("graphics.scenery:jvrpn:1.1.0")
    runtimeOnly("graphics.scenery", "jvrpn", classifier = lwjglNatives)
    sci("io.scif:scifio")
    implementation("org.bytedeco:ffmpeg:4.2.1-1.5.2")
    runtimeOnly("org.bytedeco", "ffmpeg", classifier = ffmpegNatives)
    implementation("org.reflections:reflections:0.9.12")
    implementation("io.github.classgraph:classgraph:4.8.86")
    implementation("sc.fiji:bigvolumeviewer:0.1.8")
    //    implementation("org.lwjglx:lwjgl3-awt:0.1.7")
    implementation("com.github.LWJGLX:lwjgl3-awt:cfd741a6")
    sci("org.janelia.saalfeldlab:n5")
    sci("org.janelia.saalfeldlab:n5-imglib2")
    listOf("core", "structure", "modfinder").forEach {
        sci("org.biojava:biojava-$it:5.3.0") {
            exclude("org.slf4j", "slf4j-api")
            exclude("org.slf4j", "slf4j-simple")
            exclude("org.apache.logging.log4j", "log4j-slf4j-impl")
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:${ktVersion}")
    implementation("org.jetbrains.kotlin:kotlin-test:${ktVersion}")
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

    testSci("junit:junit")
    testSci("org.slf4j:slf4j-simple")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testSci("net.imagej:imagej")
    testSci("net.imagej:ij")
    testSci("net.imglib2:imglib2-ij")
    //    testImplementation("io.kotest:kotest-runner-junit5-jvm:${findProperty("kotestVersion")}")
    //    testImplementation("io.kotest:kotest-assertions-core-jvm:${findProperty("kotestVersion")}")
}


tasks {
    withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        }
        sourceCompatibility = "11"
    }
    // https://docs.gradle.org/current/userguide/java_testing.html#test_filtering
    test {
        // apparently `testLotsOfProteins` needs also a lot of heap..
        maxHeapSize = "1G"
        // [Debug] before running every test, prints out its name
//        beforeTest(closureOf<TestDescriptor?> { logger.lifecycle("Running test: $this") })
        filter { excludeTestsMatching("ExampleRunner") }
    }
    register("testMeAll", Test::class) {
        filter {
            includeTestsMatching("ExampleRunner")
        }
    }
    jar {
        archiveVersion.set(rootProject.version.toString())
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

val sourceJar = task("sourceJar", Jar::class) {
    dependsOn(tasks.classes)
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

artifacts {
    archives(dokkaJavadocJar)
    archives(dokkaHtmlJar)
    archives(sourceJar)
}