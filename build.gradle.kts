import org.apache.tools.ant.taskdefs.condition.Os
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
    jacoco
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

    listOf("windows-amd64", "linux-i586", "linux-amd64", "macosx-universal").forEach {
        sciJava("org.jogamp.gluegen:gluegen-rt", "natives-$it") // this is crap, but will be polished eventually
        sciJava("org.jogamp.jogl:jogl-all", "natives-$it")
    }
    sciJava("org.slf4j:slf4j-api")
    sciJava("net.clearvolume:cleargl")
    sciJava("org.joml:joml")
    sciJava("com.github.scenerygraphics:vector:958f2e6")
    sciJava("net.java.jinput:jinput:2.0.9", native = "natives-all")
    listOf("scijava-common", "script-editor", /*"ui-behaviour", overwrite! */ "scripting-javascript", "scripting-jython").forEach {
        sciJava("org.scijava:$it")
    }
    sciJava("org.scijava:ui-behaviour:2.0.2")
    sciJava("net.sf.trove4j:trove4j")
    sciJava("net.java.dev.jna:jna")
    sciJava("net.java.dev.jna:jna-platform:\$jna")
    implementation("org.jocl:jocl:2.0.2")
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    fun runtimeOnlylwjglNatives(group: String, name: String) {
        listOf("windows", "linux", "macos").forEach {
            runtimeOnly(group, name, classifier = "natives-$it") // "
        }
    }
    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        implementation("org.lwjgl:lwjgl$it")
        if (it != "-vulkan")
            runtimeOnlylwjglNatives("org.lwjgl", "lwjgl$it") // "
    }
    sciJava("com.fasterxml.jackson.core:jackson-databind")
    sciJava("com.fasterxml.jackson.module:jackson-module-kotlin:\$jackson")
    sciJava("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:\$jackson")
    implementation("graphics.scenery:spirvcrossj:0.7.0-1.1.106.0")
    runtimeOnlylwjglNatives("graphics.scenery", "spirvcrossj") // "
    implementation("org.zeromq:jeromq:0.4.3")
    implementation("com.esotericsoftware:kryo:4.0.2")
    implementation("org.msgpack:msgpack-core:0.8.20")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.8.20")
    implementation("graphics.scenery:jvrpn:1.1.0")
    runtimeOnlylwjglNatives("graphics.scenery", "jvrpn") // "
    runtimeOnly("graphics.scenery", "jvrpn", classifier = "natives-linux")
    runtimeOnly("graphics.scenery", "jvrpn", classifier = "natives-macos")
    sciJava("io.scif:scifio")
    implementation("org.bytedeco:ffmpeg:4.2.1-1.5.2")
    listOf("windows", "linux", "macosx").forEach {
        runtimeOnly("org.bytedeco", "ffmpeg", classifier = "$it-x86_64") // "
    }
    implementation("org.reflections:reflections:0.9.12")
    implementation("io.github.classgraph:classgraph:4.8.86")
    implementation("sc.fiji:bigvolumeviewer:0.1.8")
    //    implementation("org.lwjglx:lwjgl3-awt:0.1.7")
    implementation("com.github.LWJGLX:lwjgl3-awt:cfd741a6")
    sciJava("org.janelia.saalfeldlab:n5")
    sciJava("org.janelia.saalfeldlab:n5-imglib2")
    listOf("core", "structure", "modfinder").forEach {
        sciJava("org.biojava:biojava-$it:5.3.0") {
            exclude("org.slf4j", "slf4j-api")
            exclude("org.slf4j", "slf4j-simple")
            exclude("org.apache.logging.log4j", "log4j-slf4j-impl")
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:$ktVersion")
    implementation("org.jetbrains.kotlin:kotlin-test:$ktVersion")
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

    testSciJava("junit:junit")
    testSciJava("org.slf4j:slf4j-simple")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testSciJava("net.imagej:imagej")
    testSciJava("net.imagej:ij")
    testSciJava("net.imglib2:imglib2-ij")
    //    testImplementation("io.kotest:kotest-runner-junit5-jvm:${findProperty("kotestVersion")}")
    //    testImplementation("io.kotest:kotest-assertions-core-jvm:${findProperty("kotestVersion")}")
}

tasks {
    withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = project.properties["jvmTarget"]?.toString() ?: "11"
            freeCompilerArgs += listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        }
        sourceCompatibility = project.properties["sourceCompatibility"]?.toString() ?: "11"
    }
    // https://docs.gradle.org/current/userguide/java_testing.html#test_filtering
    test {
        // apparently `testLotsOfProteins` needs also a lot of heap..
        maxHeapSize = "1G"
        // [Debug] before running every test, prints out its name
        //        beforeTest(closureOf<TestDescriptor?> { logger.lifecycle("Running test: $this") })
        val gpuPresent = project.properties["gpu"]?.toString()?.toBoolean() == true
        if (!gpuPresent)
            filter { excludeTestsMatching("ExampleRunner") }
        finalizedBy(jacocoTestReport) // report is always generated after tests run
    }
    register("testMeAll", Test::class) { // lets take this for comfortability in local development
        filter { includeTestsMatching("ExampleRunner") }
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

    jacocoTestReport {
        reports {
            xml.isEnabled = true
            html.apply {
                isEnabled = false
                //                destination = file("$buildDir/jacocoHtml")
            }
        }
        dependsOn(test) // tests are required to run before generating the report
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

java {
    withJavadocJar()
    withSourcesJar()
}
