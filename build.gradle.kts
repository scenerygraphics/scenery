import org.gradle.kotlin.dsl.api
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import scenery.*
import java.net.URL

plugins {
    // kotlin and dokka versions are now managed in settings.gradle.kts and gradle.properties
    kotlin("jvm")
    kotlin("kapt")
    id("org.jetbrains.dokka")

    scenery.base
    scenery.publish
    scenery.sign
//    id("com.github.elect86.sciJava") version "0.0.4"
    jacoco
    id("com.github.johnrengelman.shadow") apply false
}

repositories {
    mavenCentral()
    maven("https://maven.scijava.org/content/groups/public")
//    maven("https://jitpack.io")
//    mavenLocal()
}

val lwjglArtifacts = listOf(
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
        "lwjgl-tinyexr",
        "lwjgl-jawt",
        "lwjgl-lz4",
        "lwjgl-zstd"
)

dependencies {
    val scijavaParentPomVersion = project.properties["scijavaParentPOMVersion"]
    val lwjglVersion = project.properties["lwjglVersion"]
    
    implementation(platform("org.scijava:pom-scijava:$scijavaParentPomVersion"))
    annotationProcessor("org.scijava:scijava-common:2.98.0")

    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.joml:joml:1.10.5")
    implementation("net.java.jinput:jinput:2.0.10", "natives-all")
    implementation("org.jocl:jocl:2.0.5")
    implementation("org.scijava:scijava-common")
    implementation("org.scijava:script-editor")
    implementation("org.scijava:ui-behaviour")
    implementation("org.scijava:scripting-jython")
    implementation("net.java.dev.jna:jna-platform:5.14.0")


    lwjglArtifacts.forEach { artifact ->
        api("org.lwjgl:$artifact:$lwjglVersion")

        lwjglNatives.forEach { native ->
            when {
                // Vulkan binaries are only necessary on macOS
                artifact.endsWith("vulkan") -> {
                    if(native.contains("macos")) {
                        logger.info("vulkan: org.lwjgl:$artifact:$lwjglVersion:$native")
                        runtimeOnly("org.lwjgl:$artifact:$lwjglVersion:$native")
                    }
                }

                // OpenVR binaries are available on all scenery-supported platforms,
                // apart from macOS/ARM64
                artifact.endsWith("openvr") -> {
                    if(!(native.contains("macos") && native.contains("arm64"))) {
                        logger.info("openvr: org.lwjgl:$artifact:$lwjglVersion:$native")
                        runtimeOnly("org.lwjgl:$artifact:$lwjglVersion:$native")
                    }
                }

                // JAWT doesn't bring natives along
                artifact.endsWith("jawt") -> {}

                else -> {
                    logger.info("else: org.lwjgl:$artifact:$lwjglVersion:$native")
                    runtimeOnly("org.lwjgl:$artifact:$lwjglVersion:$native")
                }
            }
        }
    }
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    implementation("org.zeromq:jeromq:0.6.0")
    implementation("com.esotericsoftware:kryo:5.6.0")
    implementation("de.javakaffee:kryo-serializers:0.45")
    implementation("org.msgpack:msgpack-core:0.9.8")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.8")
    api("graphics.scenery:jvrpn:1.2.0", lwjglNatives.filter { !it.contains("arm") }.toTypedArray())
    implementation("io.scif:scifio")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10", ffmpegNatives)
    implementation("io.github.classgraph:classgraph:4.8.172")

    implementation("info.picocli:picocli:4.7.6")

    api("sc.fiji:bigdataviewer-core:10.6.3")
    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-28")
    api("sc.fiji:bigvolumeviewer:0.3.3") {
        exclude("org.jogamp.gluegen", "gluegen-rt")
        exclude("org.jogamp.jogl", "jogl-all")
    }

    implementation("com.github.skalarproduktraum:lwjgl3-awt:c034a77") {
        // we exclude the LWJGL binaries here, as the lwjgl3-awt POM uses
        // Maven properties for natives, which is not supported by Gradle
        exclude("org.lwjgl", "lwjgl-bom")
        exclude("org.lwjgl", "lwjgl")
    }

    implementation("org.janelia.saalfeldlab:n5")
    implementation("org.janelia.saalfeldlab:n5-imglib2")
    listOf("core", "structure", "modfinder").forEach {
        implementation("org.biojava:biojava-$it:6.0.5") {
            exclude("org.slf4j", "slf4j-api")
            exclude("org.slf4j", "slf4j-simple")
            exclude("org.apache.logging.log4j", "log4j-slf4j-impl")
            exclude("org.biojava.thirdparty", "forester")
        }
    }
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:2.0.0")
    api("graphics.scenery:art-dtrack-sdk:2.6.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

//    testImplementation(misc.junit4)
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
    testImplementation("net.imagej:imagej")
    testImplementation("net.imagej:ij")
    testImplementation("net.imglib2:imglib2-ij")

    implementation("org.jfree:jfreechart:1.5.4")
    implementation("net.imagej:imagej-ops:2.1.0")
}

val isRelease: Boolean
    get() = System.getProperty("release") == "true"

tasks {
    withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = project.properties["jvmTarget"]?.toString() ?: "21"
            freeCompilerArgs += listOf("-Xinline-classes", "-opt-in=kotlin.RequiresOptIn")
        }
    }

    withType<JavaCompile>().all {
        targetCompatibility = project.properties["jvmTarget"]?.toString() ?: "21"
        sourceCompatibility = project.properties["jvmTarget"]?.toString() ?: "21"
    }

    withType<GenerateMavenPom>().configureEach {
        fun groovy.util.Node.addExclusions(vararg name: String) {
            val exclusions = this.appendNode("exclusions")
            name.forEach { ga ->
                val group = ga.substringBefore(":")
                val artifact = ga.substringAfterLast(":")

                val n = exclusions.appendNode("exclusion")
                n.appendNode("groupId", group)
                n.appendNode("artifactId", artifact)

                logger.warn("Added exclusion on $group:$artifact")
            }
        }

        fun groovy.util.Node.addDependency(
            group: String,
            artifact: String,
            version: String,
            classifier: String? = null,
            scope: String? = null
        ): groovy.util.Node {
            val d = this.appendNode("dependency")
            d.appendNode("groupId", group)
            d.appendNode("artifactId", artifact)
            d.appendNode("version", version)
            classifier?.let { cl -> d.appendNode("classifier", cl) }
            scope?.let { sc -> d.appendNode("scope", sc) }

            return d
        }

//        val matcher = Regex("""generatePomFileFor(\w+)Publication""").matchEntire(name)
//        val publicationName = matcher?.let { it.groupValues[1] }

        pom.properties.empty()

        pom.withXml {
            val scijavaParentPomVersion = project.properties["scijavaParentPOMVersion"]
            // Add parent to the generated pom
            val parent = asNode().appendNode("parent")
            parent.appendNode("groupId", "org.scijava")
            parent.appendNode("artifactId", "pom-scijava")
            parent.appendNode("version", "$scijavaParentPomVersion")
            parent.appendNode("relativePath")

            val repositories = asNode().appendNode("repositories")

            val scijavaRepo = repositories.appendNode("repository")
            scijavaRepo.appendNode("id", "scijava.public")
            scijavaRepo.appendNode("url", "https://maven.scijava.org/content/groups/public")


            // Update the dependencies and properties
            val dependenciesNode = asNode().appendNode("dependencies")
            val propertiesNode = asNode().appendNode("properties")
            propertiesNode.appendNode("inceptionYear", 2016)

            // lwjgl natives
            lwjglNatives.forEach { nativePlatform ->
                lwjglArtifacts.forEach pkg@ { lwjglProject ->
                    // OpenVR does not have macOS binaries, Vulkan only has macOS binaries
                    if(lwjglProject.contains("vulkan")
                        && !nativePlatform.contains("mac")) {
                        return@pkg
                    }

                    // JAWT doesn't have any natives
                    if(lwjglProject.contains("jawt")) {
                        return@pkg
                    }

                    if(lwjglProject.contains("openvr")
                        && nativePlatform.contains("mac")
                        && nativePlatform.contains("arm64")) {
                        return@pkg
                    }

                    dependenciesNode.addDependency(
                        "org.lwjgl",
                        lwjglProject,
                        "\${lwjgl.version}",
                        classifier = nativePlatform,
                        scope = "runtime")
                }
            }

            // jvrpn natives
            lwjglNatives.filter { !it.contains("arm") }.forEach {
                dependenciesNode.addDependency(
                    "graphics.scenery",
                    "jvrpn",
                    "\${jvrpn.version}",
                    classifier = it,
                    scope = "runtime")
            }
            // add jvrpn property because it only has runtime native deps
            propertiesNode.appendNode("jvrpn.version", "1.2.0")

            // jinput natives
            dependenciesNode.addDependency(
                "net.java.jinput",
                "jinput",
                "2.0.9",
                classifier = "natives-all",
                scope = "runtime")

            val versionedArtifacts = mutableListOf(
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
                "jna-platform",
                "lwjgl-bom",
                "jackson-module-kotlin",
                "jackson-dataformat-yaml",
                "kryo",
                "bigvolumeviewer",
                "snappy-java"
                ) + lwjglArtifacts

            val toSkip = listOf("pom-scijava")

            configurations.implementation.get().allDependencies.forEach {
                val artifactId = it.name

                if( !toSkip.contains(artifactId) ) {
                    val propertyName = "$artifactId.version"

                    if( versionedArtifacts.contains(artifactId) ) {
                        // add "<artifactid.version>[version]</artifactid.version>" to pom
                        propertiesNode.appendNode(propertyName, it.version)
                    }

                    val node = dependenciesNode.addDependency(
                        it.group!!,
                        artifactId,
                        "\${$propertyName}")

                    // Custom per artifact tweaks
                    if("\\-bom".toRegex().find(artifactId) != null) {
                        node.appendNode("type", "pom")
                    }
                    // from https://github.com/scenerygraphics/sciview/pull/399#issuecomment-904732945
                    if(artifactId == "formats-gpl") {
                        node.addExclusions(
                            "com.fasterxml.jackson.core:jackson-core",
                            "com.fasterxml.jackson.core:jackson-annotations"
                        )
                    }

                    if(artifactId == "bigvolumeviewer") {
                        node.addExclusions(
                            "org.jogamp.gluegen:gluegen-rt",
                            "org.jogamp.jogl:jogl-all"
                        )
                    }

                    if(artifactId.startsWith("biojava")) {
                        node.addExclusions(
                            "org.slf4j:slf4j-api",
                            "org.slf4j:slf4j-simple",
                            "org.apache.logging.log4j:log4j-slf4j-impl",
                            "org.biojava.thirdparty:forester"
                        )
                    }

                    if(artifactId.startsWith("lwjgl3-awt")) {
                        node.addExclusions(
                            "org.lwjgl:lwjgl",
                            "org.lwjgl:lwjgl-bom",
                            "org.lwjgl:lwjgl-opengl",
                            "org.lwjgl:lwjgl-vulkan",
                            "org.lwjgl:lwjgl-jawt"
                        )
                    }
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
                localDirectory = file("src/main/kotlin")
                remoteUrl = URL("https://github.com/scenerygraphics/scenery/tree/main/src/main/kotlin")
                remoteLineSuffix = "#L"
            }
        }
    }

    dokkaJavadoc {
        enabled = isRelease
    }

    if(project.properties["buildFatJAR"] == true) {
        apply(plugin = "com.github.johnrengelman.shadow")
        jar {
            isZip64 = true
        }
    }
}

jacoco.toolVersion = "0.8.11"

java.withSourcesJar()

plugins.withType<JacocoPlugin> {
    tasks.test { finalizedBy("jacocoTestReport") }
}

// disable Gradle metadata file in general, as Maven artifacts are our main publication.
tasks.withType<GenerateModuleMetadata> {
    enabled = false
}
