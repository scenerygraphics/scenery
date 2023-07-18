package scenery

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    jacoco
}

tasks {

    // https://docs.gradle.org/current/userguide/java_testing.html#test_filtering
    test {
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)

            showExceptions = true
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showStackTraces = true

            showStandardStreams = false
        }

        if(JavaVersion.current() > JavaVersion.VERSION_11) {
            allJvmArgs = allJvmArgs + listOf(
                // kryo compatability
                // from https://github.com/EsotericSoftware/kryo/blob/cb255af4f8df4f539778a325b8b4836d41f84de9/pom.xml#L435
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED",
                "--add-opens=java.base/java.time=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
            )
        }
        // apparently `testLotsOfProteins` needs also a lot of heap..
        maxHeapSize = "8G"
        // [Debug] before running every test, prints out its name
        //        beforeTest(closureOf<TestDescriptor?> { logger.lifecycle("Running test: $this") })
        val gpuPresent = project.properties["gpu"]?.toString()?.toBoolean() == true
        //        println("gpuPresent=$gpuPresent")
        if (!gpuPresent) {
            filter { excludeTestsMatching("ExampleRunner") }
        } else {
            val testGroup = System.getProperty("scenery.ExampleRunner.TestGroup", "unittest")
            val testConfig = System.getProperty("scenery.ExampleRunner.Configurations", "None")

            configure<JacocoTaskExtension> {
                setDestinationFile(file("$buildDir/jacoco/jacocoTest.$testGroup.$testConfig.exec"))
                println("Destination file for jacoco is $destinationFile (test, $testGroup, $testConfig)")
            }

            filter { excludeTestsMatching("graphics.scenery.tests.unit.**") }

            // this should circumvent Nvidia's Vulkan cleanup issue
            maxParallelForks = 1
            setForkEvery(1)
            systemProperty("scenery.Workarounds.DontCloseVulkanInstances", "true")

            testLogging {
                exceptionFormat = TestExceptionFormat.FULL
                events = mutableSetOf(TestLogEvent.PASSED,
                    TestLogEvent.FAILED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STARTED)
                showStandardStreams = true
            }

            // we only want the Vulkan renderer here, and all screenshot to be stored in the screenshots/ dir
            systemProperty("scenery.Renderer", "VulkanRenderer")
            systemProperty("scenery.ExampleRunner.OutputDir", "screenshots")

            val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

            println("Adding properties ${props.size}/$props")
            val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
            allJvmArgs = if (additionalArgs != null) {
                allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
            } else {
                allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
            }
        }

        finalizedBy(jacocoTestReport) // report is always generated after tests run
    }

    register<Test>("testGpu") { // lets take this for comfortability in local development
        maxHeapSize = "8G"
        group = "verification"
        filter { includeTestsMatching("ExampleRunner") }

        val testGroup = System.getProperty("scenery.ExampleRunner.TestGroup", "basic")
        extensions.configure<JacocoTaskExtension> {
            setDestinationFile(layout.buildDirectory.file("jacoco/jacocoTest.$testGroup.exec").get().asFile)
        }
    }

    register<JavaExec>("compileShader") {
        group = "tools"
        mainClass.set("graphics.scenery.backends.ShaderCompiler")
        classpath = sourceSets["main"].runtimeClasspath

    }

    register<JacocoReport>("fullCodeCoverageReport") {
        executionData(fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec"))

        sourceSets(sourceSets["main"], sourceSets["test"])

        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }

        dependsOn(test)
    }

    named<Jar>("jar") {
        archiveVersion.set(rootProject.version.toString())
        manifest.attributes["Implementation-Build"] = run { // retrieve the git commit hash
            val gitFolder = "$projectDir/.git/"
            val digit = 6
            /*  '.git/HEAD' contains either
             *      in case of detached head: the currently checked out commit hash
             *      otherwise: a reference to a file containing the current commit hash     */
            val head = file(gitFolder + "HEAD").readText().split(":") // .git/HEAD
            val isCommit = head.size == 1 // e5a7c79edabbf7dd39888442df081b1c9d8e88fd
            // def isRef = head.length > 1     // ref: refs/heads/master
            when {
                isCommit -> head[0] // e5a7c79edabb
                else -> file(gitFolder + head[1].trim()) // .git/refs/heads/master
                    .readText()
            }.trim().take(digit)
        }
    }

    jacocoTestReport {
        reports {
            xml.required.set(true)
            html.required.set(false)
        }
        dependsOn(test) // tests are required to run before generating the report
    }

    // This registers gradle tasks for all examples
    sourceSets.test.get().allSource.files
        .filter { it.name.endsWith("Example.kt") }
        .map { it.path.substringAfter("kotlin${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".kt") }
        .forEach { className ->
            val exampleName = className.substringAfterLast(".")
            val exampleType = className.substringBeforeLast(".").substringAfterLast(".")

            register<JavaExec>(name = exampleName) {
                classpath = sourceSets.test.get().runtimeClasspath
                mainClass.set(className)
                group = "examples.$exampleType"

                val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

                val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
                allJvmArgs = if (additionalArgs != null) {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
                } else {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
                }

                if(JavaVersion.current() > JavaVersion.VERSION_11) {
                    allJvmArgs = allJvmArgs + listOf(
                        // kryo compatability
                        // from https://github.com/EsotericSoftware/kryo/blob/cb255af4f8df4f539778a325b8b4836d41f84de9/pom.xml#L435
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                        "--add-opens=java.base/java.net=ALL-UNNAMED",
                        "--add-opens=java.base/java.nio=ALL-UNNAMED",
                        "--add-opens=java.base/java.time=ALL-UNNAMED",
                        "--add-opens=java.base/java.util=ALL-UNNAMED",
                        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                        "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
                    )
                }
            }
        }

    register<JavaExec>("run") {
        classpath = sourceSets.test.get().runtimeClasspath
        if (project.hasProperty("example")) {
            project.property("example")?.let { example ->
                val file = sourceSets.test.get().allSource.files.first { "class $example" in it.readText() }
                mainClass.set(file.path.substringAfter("kotlin${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".kt"))
                val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

                val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
                allJvmArgs = if (additionalArgs != null) {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
                } else {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
                }

                if(JavaVersion.current() > JavaVersion.VERSION_11) {
                    allJvmArgs = allJvmArgs + listOf(
                        // kryo compatability
                        // from https://github.com/EsotericSoftware/kryo/blob/cb255af4f8df4f539778a325b8b4836d41f84de9/pom.xml#L435
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                        "--add-opens=java.base/java.net=ALL-UNNAMED",
                        "--add-opens=java.base/java.nio=ALL-UNNAMED",
                        "--add-opens=java.base/java.time=ALL-UNNAMED",
                        "--add-opens=java.base/java.util=ALL-UNNAMED",
                        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                        "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
                    )
                }

                println("Will run example $example with classpath $classpath, main=${mainClass.get()}")
                println("JVM arguments passed to example: $allJvmArgs")
            }
        }
    }
}

val TaskContainer.jacocoTestReport: TaskProvider<JacocoReport>
    get() = named<JacocoReport>("jacocoTestReport")

val TaskContainer.test: TaskProvider<Test>
    get() = named<Test>("test")

val Project.sourceSets: SourceSetContainer
    get() = (this as ExtensionAware).extensions.getByName("sourceSets") as SourceSetContainer

val SourceSetContainer.test: NamedDomainObjectProvider<SourceSet>
    get() = named<SourceSet>("test")
