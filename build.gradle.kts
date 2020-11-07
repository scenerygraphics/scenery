import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    java
    kotlin("jvm") version "1.4.10"
    publish
    id("org.jetbrains.dokka") version "1.4.10" //    id("com.github.johnrengelman.shadow") version "6.0.0"
    //    idea
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
    maven("https://maven.scijava.org/content/groups/public")
}

//
//val uiBehaviourVersion = "2.0.2"
//
//val bigvolumeviewerVersion = "0.1.8"
//val bigdataviewerCoreVersion = "8.0.0"
//val ffmpegVersion = "4.2.1-1.5.2"
//<jackson-dataformat-msgpack.version>0.8.20</jackson-dataformat-msgpack.version>
//<jeromq.version>0.4.3</jeromq.version>
//<jinput.version>2.0.9</jinput.version>
//<jocl.version>2.0.2</jocl.version>
//<jvrpn.version>1.1.0</jvrpn.version>
//<kotlinx-coroutines-core.version>1.3.9</kotlinx-coroutines-core.version>
//<kryo.version>4.0.2</kryo.version>
//<lwjgl.version>3.2.3</lwjgl.version>
//<lwjgl3-awt.version>0.1.7</lwjgl3-awt.version>
//<msgpack-core.version>0.8.20</msgpack-core.version>
//<classgraph.version>4.8.86</classgraph.version>
//<spirvcrossj.version>0.7.0-1.1.106.0</spirvcrossj.version>

val pomSci = URL("https://raw.githubusercontent.com/scijava/pom-scijava/master/pom.xml").readText()

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-M1")

    implementation(platform("org.scijava:pom-scijava:29.2.1"))
    implementation(platform("org.scijava:pom-scijava-base:11.2.0"))

    components.all<Rule>()

//    sci("org.jogamp.gluegen:gluegen-rt") //    implementation("org.jogamp.gluegen:gluegen-rt-main")
    //    runtimeOnly("org.jogamp.gluegen", "gluegen-rt", classifier = joglNative)
    //    implementation("org.jogamp.jogl:jogl-all-main") // -main "
    //    runtimeOnly("org.jogamp.jogl", "jogl-all", classifier = joglNative)
    //    implementation("org.slf4j:slf4j-api")
    //    implementation("org.slf4j:slf4j-simple")
    //    implementation("net.clearvolume:cleargl")
    //    implementation("org.joml:joml")
    //    implementation("com.github.scenerygraphics:vector:958f2e6")
    //    implementation("net.java.jinput:jinput:2.0.9")
    //    runtimeOnly("net.java.jinput", "jinput", "2.0.9", classifier = "natives-all")
    //    listOf("scijava-common", "script-editor", "ui-behaviour", "scripting-javascript", "scripting-jython").forEach {
    //        implementation("org.scijava:$it")
    //    }
    //    implementation("net.sf.trove4j:trove4j")
    //        implementation("net.java.dev.jna:jna")
    //        implementation("net.java.dev.jna:jna-platform")
    //    implementation("org.jocl:jocl")
    //    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    //    listOf("", "-glfw", "-jemmalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
    //        implementation("org.lwjgl:lwjgl$it")
    //        if (it != "-vulkan")
    //            runtimeOnly("org.lwjgl", "lwjgl$it", classifier = lwjglNatives)
    //    }
    //    implementation("com.fasterxml.jackson.core:jackson-databind")
    //    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    //    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    //    implementation("graphics.scenery:spirvcrossj")
    //    runtimeOnly("graphics.scenery", "spirvcrossj", classifier = lwjglNatives)
    //    implementation("org.zeromq:jeromq")
    //    implementation("com.esotericsoftware:kryo")
    //    implementation("org.msgpack:msgpack-core")
    //    implementation("org.msgpack:jackson-dataformat-msgpack")
    //    implementation("graphics.scenery:jvrpn")
    //    runtimeOnly("graphics.scenery", "jvrpn", classifier = lwjglNatives)
    //    implementation("io.scif:scifio")
    //    implementation("org.bytedeco:ffmpeg")
    //    runtimeOnly("org.bytedeco", "ffmpeg", classifier = ffmpegNatives)
    //    implementation("org.reflections:reflections")
    //    implementation("io.github.classgraph:classgraph")
    //    implementation("sc.fiji:bigvolumeviewer")
    //    implementation("org.lwjglx:lwjgl3-awt")
    //    implementation("net.imagej:imagej")
    //    implementation("net.imagej:ij")
    //    implementation("org.janelia.saalfeldlab:n5")
    //    implementation("rg.janelia.saalfeldlab:n5-imglib2")

    //    testImplementation("junit:junit")
    //    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    //    testImplementation("net.imglib2:imglib2-ij")
    //    testImplementation("io.kotest:kotest-runner-junit5-jvm:${findProperty("kotestVersion")}")
    //    testImplementation("io.kotest:kotest-assertions-core-jvm:${findProperty("kotestVersion")}")
}

fun DependencyHandlerScope.sci(dep: String) {
    val vers = dep.substringAfterLast(':').version
    println("implementation(\"$dep:$vers\")")
    implementation("$dep:$vers")
}

val String.version: String
    get() {
        val start = pomSci.indexOf('>', startIndex = pomSci.indexOf(this))
        val end = pomSci.indexOf('<', startIndex = start)
        check(start != -1 && end != -1)
        val vers = pomSci.substring(start + 1, end)
        println("vers=$vers")
        return when {
            vers.startsWith("\${") && vers.endsWith("}") -> vers.version // recursive, ie: ${jogl.version}
            else -> vers
        }
    }

open class Rule : ComponentMetadataRule {
    override fun execute(ctx: ComponentMetadataContext) {
        ctx.details.run {
            if (id.group.startsWith("net.java.dev.jna")) belongsTo("net.java.dev.jna:jna-virtual-platform:${id.version}")
        }
    }
}

val is64: Boolean = run {
    val arch = System.getenv("PROCESSOR_ARCHITECTURE")
    val wow64Arch = System.getenv("PROCESSOR_ARCHITEW6432")
    arch?.endsWith("64") == true || wow64Arch?.endsWith("64") == true
}

val os = DefaultNativePlatform.getCurrentOperatingSystem()

val joglNative = "natives-" + when {
    os.isWindows -> "windows".also { check(is64) }
    os.isLinux -> "linux-" + if (is64) "i586" else "amd64"
    os.isMacOsX -> "macosx-universal"
    else -> error("invalid")
}

val lwjglNatives = "natives-" + when {
    os.isWindows -> "windows"
    os.isLinux -> "linux"
    os.isMacOsX -> "macos"
    else -> error("invalid")
}

val ffmpegNatives = when {
    os.isWindows -> "windows"
    os.isLinux -> "linux"
    os.isMacOsX -> "macosx"
    else -> error("invalid")
} + "-x86_64"

val pom = URL("https://github.com/scijava/pom-scijava/blob/master/pom.xml").readText()

tasks {

    withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        }
        sourceCompatibility = "11"
    }
}

//val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
//    dependsOn(tasks.dokkaJavadoc)
//    from(tasks.dokkaJavadoc.get().outputDirectory.get())
//    archiveClassifier.set("javadoc")
//}
//
//val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
//    dependsOn(tasks.dokkaHtml)
//    from(tasks.dokkaHtml.get().outputDirectory.get())
//    archiveClassifier.set("html-doc")
//}
//
//val sourceJar = task("sourceJar", Jar::class) {
//    dependsOn(tasks.classes)
//    archiveClassifier.set("sources")
//    from(sourceSets.main.get().allSource)
//}
//
//artifacts {
//    archives(dokkaJavadocJar)
//    archives(dokkaHtmlJar)
//    archives(sourceJar)
//}

val sciBom: MutableMap<String, String> = run {

    mutableMapOf()
}
