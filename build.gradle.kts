import org.gradle.kotlin.dsl.api
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import scenery.*
import java.net.URL
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val ktVersion = "1.5.0"
    java
    kotlin("jvm") version ktVersion
    kotlin("kapt") version ktVersion
    scenery.base
//    scenery.docs
    scenery.publish
    scenery.sign
    id("org.jetbrains.dokka") version "1.4.30"
    jacoco
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
}

dependencies {
    implementation(platform("org.scijava:pom-scijava:31.1.0"))
    annotationProcessor("org.scijava:scijava-common")
//    kapt("org.scijava:scijava-common")

//    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

    implementation("org.jogamp.gluegen:gluegen-rt:2.3.2", joglNatives)
    implementation("org.jogamp.jogl:jogl-all:2.3.2", joglNatives)
    implementation("org.slf4j:slf4j-api")
    implementation("net.clearvolume:cleargl")
//    implementation("org.joml")
    implementation("net.java.jinput:jinput:2.0.9", "natives-all")
    implementation("org.jocl:jocl:2.0.2")
    implementation("org.scijava:scijava-common")
    implementation("org.scijava:script-editor")
    implementation("org.scijava:ui-behaviour")
    implementation("org.scijava:scripting-javascript")
    implementation("org.scijava:scripting-jython")
//    implementation(misc.trove)
    implementation("net.java.dev.jna:jna-platform:5.9.0")
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))
    listOf("", "-glfw", "-jemalloc", "-vulkan", "-opengl", "-openvr", "-xxhash", "-remotery").forEach {
        if (it == "-vulkan")
            api("org.lwjgl:lwjgl$it:3.2.3")
        else
            api("org.lwjgl:lwjgl$it:3.2.3", lwjglNatives)
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.5")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.5")
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

//    testImplementation(misc.junit4)
//    testImplementation(slf4j.simple)
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

    withType<GenerateMavenPom>().configureEach {
        val matcher = Regex("""generatePomFileFor(\w+)Publication""").matchEntire(name)
        val publicationName = matcher?.let { it.groupValues[1] }

        pom.properties.empty()

        pom.withXml {
            // Add parent to the generated pom
            var parent = asNode().appendNode("parent")
            parent.appendNode("groupId", "org.scijava")
            parent.appendNode("artifactId", "pom-scijava")
            parent.appendNode("version", "31.1.0")
            parent.appendNode("relativePath")

            var repositories = asNode().appendNode("repositories")
            var jitpackRepo = repositories.appendNode("repository")
            jitpackRepo.appendNode("id", "jitpack.io")
            jitpackRepo.appendNode("url", "https://jitpack.io")

            var scijavaRepo = repositories.appendNode("repository")
            scijavaRepo.appendNode("id", "scijava.public")
            scijavaRepo.appendNode("url", "https://maven.scijava.org/content/groups/public")

            
            // Update the dependencies and properties
            var dependenciesNode = asNode().appendNode("dependencies")
            var propertiesNode = asNode().appendNode("properties")
            propertiesNode.appendNode("inceptionYear", 2016)

            val versionedArtifacts = listOf(
                "flatlaf",
                "kotlin-stdlib-common",
                "kotlin-stdlib",
                "kotlinx-coroutines-core",
                "jocl",
                "jeromq",
                "kryo-serializers",
                "msgpack-core",
                "jackson-dataformat-msgpack",
                "ffmpeg",
                "reflections",
                "classgraph",
                "lwjgl3-awt",
                "biojava-core",
                "biojava-structure",
                "biojava-modfinder",
                "kotlin-scripting-jsr223",
                "art-dtrack-sdk",
                "spirvcrossj",
                "jvrpn",
                "jogl-minimal",
                "jinput",
                "pom-scijava",
                "gluegen-rt",
                "jogl-all",
                "jna-platform",
                "lwjgl-bom",
                "jackson-module-kotlin",
                "jackson-dataformat-yaml",
                "kryo",
                "lwjgl",
                "lwjgl-glfw",
                "lwjgl-jemalloc",
                "lwjgl-vulkan",
                "lwjgl-opengl",
                "lwjgl-openvr",
                "lwjgl-xxhash",
                "lwjgl-remotery")

            val toSkip = listOf("pom-scijava")
            
            configurations.implementation.allDependencies.forEach {
                var artifactId = it.name

                if( !toSkip.contains(artifactId) ) {
                    
                    var propertyName = "$artifactId.version"

                    if( versionedArtifacts.contains(artifactId) ) {
                        // add "<artifactid.version>[version]</artifactid.version>" to pom
                        propertiesNode.appendNode(propertyName, it.version)
                    }

                    var dependencyNode = dependenciesNode.appendNode("dependency")
                    dependencyNode.appendNode("groupId", it.group)
                    dependencyNode.appendNode("artifactId", artifactId)
                    dependencyNode.appendNode("version", "\${$propertyName}")

                    // Custom per artifact tweaks
                    println(artifactId)
                    if("\\-bom".toRegex().find(artifactId) != null) {
                        dependencyNode.appendNode("type", "pom")
                    }
                    // from https://github.com/scenerygraphics/sciview/pull/399#issuecomment-904732945
                    if(artifactId == "formats-gpl") {
                        var exclusions = dependencyNode.appendNode("exclusions")
                        var jacksonCore = exclusions.appendNode("exclusion")
                        jacksonCore.appendNode("groupId", "com.fasterxml.jackson.core")
                        jacksonCore.appendNode("artifactId", "jackson-core")
                        var jacksonAnnotations = exclusions.appendNode("exclusion")
                        jacksonAnnotations.appendNode("groupId", "com.fasterxml.jackson.core")
                        jacksonAnnotations.appendNode("artifactId", "jackson-annotations")
                    }
                    //dependencyNode.appendNode("scope", it.scope)
                }
            }

            var depStartIdx = "<dependencyManagement>".toRegex().find(asString())?.range?.start
            var depEndIdx = "</dependencyManagement>".toRegex().find(asString())?.range?.last
            if (depStartIdx != null) {
                if (depEndIdx != null) {
                    asString().replace(depStartIdx, depEndIdx+1, "")
                }
            }

            depStartIdx = "<dependencies>".toRegex().find(asString())?.range?.start
            depEndIdx = "</dependencies>".toRegex().find(asString())?.range?.last
            if (depStartIdx != null) {
                if (depEndIdx != null) {
                    asString().replace(depStartIdx, depEndIdx+1, "")
                }
            }
        }
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
    if(isRelease) {
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
if(System.getenv("JITPACK") != null) {
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
}

