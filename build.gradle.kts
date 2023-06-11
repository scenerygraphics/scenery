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
//    scenery.docs
    scenery.publish
    scenery.sign
//    id("com.github.elect86.sciJava") version "0.0.4"
    jacoco
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://jitpack.io")
//    mavenLocal()
}

dependencies {
    implementation(platform("org.scijava:pom-scijava:31.1.0"))
    annotationProcessor("org.scijava:scijava-common:2.88.1")

    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")

    implementation("org.jogamp.gluegen:gluegen-rt:2.3.2", joglNatives)
    implementation("org.jogamp.jogl:jogl-all:2.3.2", joglNatives)
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("net.clearvolume:cleargl")
    implementation("org.joml:joml:1.10.5")
    implementation("net.java.jinput:jinput:2.0.9", "natives-all")
    implementation("org.jocl:jocl:2.0.4")
    implementation("org.scijava:scijava-common")
    implementation("org.scijava:script-editor")
    implementation("org.scijava:ui-behaviour")
    implementation("org.scijava:scripting-javascript")
    implementation("org.scijava:scripting-jython")
    implementation("net.java.dev.jna:jna-platform:5.11.0")

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
    ).forEach { p ->
        api("org.lwjgl:lwjgl$p:$lwjglVersion")

        lwjglNatives.forEach { native ->
            when {
                // Vulkan binaries are only necessary on macOS
                p.endsWith("vulkan") -> {
                    if(native.contains("macos")) {
                        logger.info("vulkan: org.lwjgl:lwjgl$p:$lwjglVersion:$native")
                        runtimeOnly("org.lwjgl:lwjgl$p:$lwjglVersion:$native")
                    }
                }

                // OpenVR binaries are available on all scenery-supported platforms,
                // apart from macOS/ARM64
                p.endsWith("openvr") -> {
                    if(!(native.contains("macos") && native.contains("arm64"))) {
                        logger.info("openvr: org.lwjgl:lwjgl$p:$lwjglVersion:$native")
                        runtimeOnly("org.lwjgl:lwjgl$p:$lwjglVersion:$native")
                    }
                }

                else -> {
                    logger.info("else: org.lwjgl:lwjgl$p:$lwjglVersion:$native")
                    runtimeOnly("org.lwjgl:lwjgl$p:$lwjglVersion:$native")
                }
            }
        }
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.3")
    implementation("org.xerial.snappy:snappy-java:1.1.8.4")
    implementation("org.lwjgl:lwjgl-lz4:3.3.0")
    implementation("org.lwjgl:lwjgl-zstd:3.3.0")
    implementation("org.lwjgl:lwjgl-lz4:3.3.0:natives-linux")
    implementation("org.lwjgl:lwjgl-zstd:3.3.0:natives-linux")
    implementation("org.zeromq:jeromq:0.5.2")
    implementation("com.esotericsoftware:kryo:5.3.0")
    implementation("de.javakaffee:kryo-serializers:0.45")
    implementation("org.msgpack:msgpack-core:0.9.1")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.1")
    api("graphics.scenery:jvrpn:1.2.0", lwjglNatives.filter { !it.contains("arm") }.toTypedArray())
    implementation("io.scif:scifio")
    implementation("org.bytedeco:ffmpeg:5.0-1.5.7", ffmpegNatives)
    implementation("io.github.classgraph:classgraph:4.8.147")

    implementation("info.picocli:picocli:4.6.3")

    api("sc.fiji:bigdataviewer-core:10.4.1")
    api("sc.fiji:bigdataviewer-vistools:1.0.0-beta-28")

    //TODO revert to official BVV
    api("graphics.scenery:bigvolumeviewer:7698a01")

    implementation("com.github.skalarproduktraum:lwjgl3-awt:d7a7369")
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
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.5.31")
    api("graphics.scenery:art-dtrack-sdk:2.6.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    //    implementation("com.github.kotlin-graphics:assimp:25c68811")

//    testImplementation(misc.junit4)
    testImplementation("org.slf4j:slf4j-simple:1.7.36")
    testImplementation("net.imagej:imagej")
    testImplementation("net.imagej:ij")
    testImplementation("net.imglib2:imglib2-ij")

    implementation("org.jfree:jfreechart:1.5.0")
    implementation("net.imagej:imagej-ops:0.45.5")
}

val isRelease: Boolean
    get() = System.getProperty("release") == "true"

tasks {

    withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = project.properties["jvmTarget"]?.toString() ?: "11"
            freeCompilerArgs += listOf("-Xinline-classes", "-opt-in=kotlin.RequiresOptIn")
        }
    }

    withType<JavaCompile>().all {
        targetCompatibility = project.properties["jvmTarget"]?.toString() ?: "11"
        sourceCompatibility = project.properties["jvmTarget"]?.toString() ?: "11"
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
            // Add parent to the generated pom
            val parent = asNode().appendNode("parent")
            parent.appendNode("groupId", "org.scijava")
            parent.appendNode("artifactId", "pom-scijava")
            parent.appendNode("version", "31.1.0")
            parent.appendNode("relativePath")

            val repositories = asNode().appendNode("repositories")
            val jitpackRepo = repositories.appendNode("repository")
            jitpackRepo.appendNode("id", "jitpack.io")
            jitpackRepo.appendNode("url", "https://jitpack.io")

            val scijavaRepo = repositories.appendNode("repository")
            scijavaRepo.appendNode("id", "scijava.public")
            scijavaRepo.appendNode("url", "https://maven.scijava.org/content/groups/public")


            // Update the dependencies and properties
            val dependenciesNode = asNode().appendNode("dependencies")
            val propertiesNode = asNode().appendNode("properties")
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
                    "-shaderc",
                    "-vulkan",
                ).forEach pkg@ { lwjglProject ->
                    // OpenVR does not have macOS binaries, Vulkan only has macOS binaries
                    if(lwjglProject.contains("vulkan")
                        && !nativePlatform.contains("mac")) {
                        return@pkg
                    }

                    if(lwjglProject.contains("openvr")
                        && nativePlatform.contains("mac")
                        && nativePlatform.contains("arm64")) {
                        return@pkg
                    }

                    dependenciesNode.addDependency(
                        "org.lwjgl",
                        "lwjgl$lwjglProject",
                        "\${lwjgl.version}",
                        classifier = "$nativePlatform",
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

                    if(artifactId.startsWith("biojava")) {
                        node.addExclusions(
                            "org.slf4j:slf4j-api",
                            "org.slf4j:slf4j-simple",
                            "org.apache.logging.log4j:log4j-slf4j-impl",
                            "org.biojava.thirdparty:forester"
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
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/scenerygraphics/scenery/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }

    shadowJar {
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

jacoco.toolVersion = "0.8.8"

java.withSourcesJar()

plugins.withType<JacocoPlugin> {
    tasks.test { finalizedBy("jacocoTestReport") }
}

// disable Gradle metadata file creation on Jitpack, as jitpack modifies
// the metadata file, resulting in broken metadata with missing native dependencies.
if(System.getenv("JITPACK") != null) {
    tasks.withType<GenerateModuleMetadata> {
        enabled = false
    }
}
