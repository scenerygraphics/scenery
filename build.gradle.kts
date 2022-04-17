import org.gradle.kotlin.dsl.api
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import scenery.*
import java.net.URL
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val ktVersion = "1.6.10"
    java
    kotlin("jvm") version ktVersion
    kotlin("kapt") version ktVersion
    scenery.base
//    scenery.docs
    scenery.publish
    scenery.sign
    id("com.github.elect86.sciJava") version "0.0.4"
    id("org.jetbrains.dokka") version "1.6.0"
    id("org.sonarqube") version "3.3"
    jacoco
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
    mavenLocal()
}

dependencies {
    implementation(platform("org.scijava:pom-scijava:31.1.0"))
    annotationProcessor("org.scijava:scijava-common:2.87.1")

    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")

    implementation("org.jogamp.gluegen:gluegen-rt:2.3.2", joglNatives)
    implementation("org.jogamp.jogl:jogl-all:2.3.2", joglNatives)
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("net.clearvolume:cleargl")
    implementation("org.joml:joml:1.10.2")
    implementation("net.java.jinput:jinput:2.0.9", "natives-all")
    implementation("org.jocl:jocl:2.0.4")
    implementation("org.scijava:scijava-common")
    implementation("org.scijava:script-editor")
    implementation("org.scijava:ui-behaviour")
    implementation("org.scijava:scripting-javascript")
    implementation("org.scijava:scripting-jython")
    implementation("net.java.dev.jna:jna-platform:5.9.0")

    val lwjglVersion = "3.3.1"
    listOf("",
        "-glfw",
        "-jemalloc",
        "-vulkan",
        "-opengl",
        "-openvr",
        "-xxhash",
        "-remotery",
        "-spvc",
        "-shaderc"
    ).forEach { lwjglProject ->
        api("org.lwjgl:lwjgl$lwjglProject:$lwjglVersion")

        lwjglNatives.forEach { native ->
            if (lwjglProject.endsWith("-vulkan")) {
                if (!native.contains("linux") && !native.contains("win")) {
                    runtimeOnly("org.lwjgl:lwjgl$lwjglProject:$lwjglVersion:$native")
                }
            }
            else if (lwjglProject.endsWith("-openvr")) {
                if (native.contains("linux") && native.contains("win")) {
                    runtimeOnly("org.lwjgl:lwjgl$lwjglProject:$lwjglVersion:$native")
                }
            } else {
                runtimeOnly("org.lwjgl:lwjgl$lwjglProject:$lwjglVersion:$native")
            }
        }
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.0")
    implementation("org.zeromq:jeromq:0.5.2")
    implementation("com.esotericsoftware:kryo:5.2.0")
    implementation("de.javakaffee:kryo-serializers:0.45")
    implementation("org.msgpack:msgpack-core:0.9.0")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.0")
    api("graphics.scenery:jvrpn:1.2.0", lwjglNatives.filter { !it.contains("arm") }.toTypedArray())
    implementation("io.scif:scifio")
    implementation("org.bytedeco:ffmpeg:4.3.2-1.5.5", ffmpegNatives)
    implementation("io.github.classgraph:classgraph:4.8.137")

    implementation("info.picocli:picocli:4.6.2")

    api("sc.fiji:bigdataviewer-core:10.2.0")
    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-28")

    //TODO revert to official BVV
    api("graphics.scenery:bigvolumeviewer:a6b021d")

//    implementation("com.github.LWJGLX:lwjgl3-awt:cfd741a6")
    implementation("com.github.skalarproduktraum:lwjgl3-awt:f298596")
    implementation("org.janelia.saalfeldlab:n5")
    implementation("org.janelia.saalfeldlab:n5-imglib2")
    listOf("core", "structure", "modfinder").forEach {
        implementation("org.biojava:biojava-$it:5.4.0") {
            exclude("org.slf4j", "slf4j-api")
            exclude("org.slf4j", "slf4j-simple")
            exclude("org.apache.logging.log4j", "log4j-slf4j-impl")
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.5.31")
    implementation("graphics.scenery:art-dtrack-sdk:2.6.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

//    testImplementation(misc.junit4)
    testImplementation("org.slf4j:slf4j-simple:1.7.32")
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

            // lwjgl natives
            lwjglNatives.forEach { nativePlatform ->
                listOf(
                    "",
                    "-glfw",
                    "-jemalloc",
                    "-opengl",
                    "-openvr",
                    "-xxhash",
                    "-remotery",
                    "-spvc",
                    "-shaderc"
                ).forEach { lwjglProject ->
                    var dependencyNode = dependenciesNode.appendNode("dependency")
                    dependencyNode.appendNode("groupId", "org.lwjgl")
                    dependencyNode.appendNode("artifactId", "lwjgl$lwjglProject")
                    dependencyNode.appendNode("version", "\${lwjgl.version}")
                    dependencyNode.appendNode("classifier", "$nativePlatform")
                    dependencyNode.appendNode("scope", "runtime")
                }
            }

            // lwjgl-vulkan native for macos
            var dependencyNodeLWJGLVulkan = dependenciesNode.appendNode("dependency")
            dependencyNodeLWJGLVulkan.appendNode("groupId", "org.lwjgl")
            dependencyNodeLWJGLVulkan.appendNode("artifactId", "lwjgl-vulkan")
            dependencyNodeLWJGLVulkan.appendNode("version", "\${lwjgl.version}")
            dependencyNodeLWJGLVulkan.appendNode("classifier", "natives-macos")
            dependencyNodeLWJGLVulkan.appendNode("scope", "runtime")

            // jvrpn natives
            lwjglNatives.forEach {
                var dependencyNode = dependenciesNode.appendNode("dependency")
                dependencyNode.appendNode("groupId", "graphics.scenery")
                dependencyNode.appendNode("artifactId", "jvrpn")
                dependencyNode.appendNode("version", "\${jvrpn.version}")
                dependencyNode.appendNode("classifier", "$it")
                dependencyNode.appendNode("scope", "runtime")
            }
            // add jvrpn property because it only has runtime native deps
            propertiesNode.appendNode("jvrpn.version", "1.2.0")

            // jinput natives
            var dependencyNode = dependenciesNode.appendNode("dependency")
            dependencyNode.appendNode("groupId", "net.java.jinput")
            dependencyNode.appendNode("artifactId", "jinput")
            dependencyNode.appendNode("version", "2.0.9")
            dependencyNode.appendNode("classifier", "natives-all")
            dependencyNode.appendNode("scope", "runtime")

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
                "lwjgl-remotery",
                "lwjgl-spvc",
                "lwjgl-shaderc",
                "bigvolumeviewer")

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

sonarqube {
    properties {
        property("sonar.projectKey", "scenerygraphics_scenery")
        property("sonar.organization", "scenerygraphics-1")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/fullCodeCoverageReport/fullCodeCoverageReport.xml")
    }
}

plugins.withType<JacocoPlugin>() {
    tasks["test"].finalizedBy("jacocoTestReport")
}

// disable Gradle metadata file creation on Jitpack, as jitpack modifies
// the metadata file, resulting in broken metadata with missing native dependencies.
if(System.getenv("JITPACK") != null) {
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
}

