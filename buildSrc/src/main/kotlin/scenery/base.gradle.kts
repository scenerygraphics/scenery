package scenery

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    jacoco
}

repositories {
    mavenCentral()
}

jacoco.toolVersion = "0.8.11"
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
                setDestinationFile(file("${layout.buildDirectory}/jacoco/jacocoTest.$testGroup.$testConfig.exec"))
                println("Destination file for jacoco is $destinationFile (test, $testGroup, $testConfig)")
            }

            filter { excludeTestsMatching("graphics.scenery.tests.unit.**") }

            // this should circumvent Nvidia's Vulkan cleanup issue
            maxParallelForks = 1
            setForkEvery(1)
            //systemProperty("scenery.Workarounds.DontCloseVulkanInstances", "true")

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
            allJvmArgs = allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
            System.getenv("SCENERY_JVM_ARGS")?.let { allJvmArgs = allJvmArgs + it }
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
        mainClass = "graphics.scenery.backends.ShaderCompiler"
        classpath = sourceSets["main"].runtimeClasspath
    }

    register<JacocoReport>("fullCodeCoverageReport") {
        executionData(fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec"))

        sourceSets(sourceSets["main"], sourceSets["test"])

        reports {
            xml.required = true
            html.required = true
            csv.required = false
        }

        dependsOn(test)
    }

    named<Jar>("jar") {
        archiveVersion = rootProject.version.toString()
        manifest.attributes["Implementation-Build"] = run { // retrieve the git commit hash
            val gitFolder = "$projectDir/.git/"
            val digit = 7
            /*  '.git/HEAD' contains either
             *      in case of detached head: the currently checked out commit hash
             *      otherwise: a reference to a file containing the current commit hash     */
            val head = file(gitFolder + "HEAD").readText().split(":") // .git/HEAD
            val isCommit = head.size == 1 // e5a7c79edabbf7dd39888442df081b1c9d8e88fd
            // def isRef = head.length > 1     // ref: refs/heads/main
            when {
                isCommit -> head[0] // e5a7c79edabb
                else -> file(gitFolder + head[1].trim()) // .git/refs/heads/main
                    .readText()
            }.trim().take(digit)
        }

        manifest.attributes["Implementation-Version"] = project.version
    }

    jacocoTestReport {
        reports {
            xml.required = true
            html.required = false
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
                mainClass = className
                group = "examples.$exampleType"

                val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

                allJvmArgs = allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
                System.getenv("SCENERY_JVM_ARGS")?.let { allJvmArgs = allJvmArgs + it }

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
                mainClass = file.path.substringAfter("kotlin${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".kt")
                val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

                allJvmArgs = allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
                System.getenv("SCENERY_JVM_ARGS")?.let { allJvmArgs = allJvmArgs + it }

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

    // this is the Kotlinized version of https://github.com/mendhak/Gradle-Github-Colored-Output
    // (c) by Mendhak, with a few adjustments, e.g. to cache failures and skips, and print them
    // in the summary.
    withType(Test::class.java) {
        val folding = System.getenv("GITHUB_ACTIONS") != null

        val ANSI_BOLD_WHITE = "\u001B[01m"
        val ANSI_RESET = "\u001B[0m"
        val ANSI_BLACK = "\u001B[30m"
        val ANSI_RED = "\u001B[31m"
        val ANSI_GREEN = "\u001B[32m"
        val ANSI_YELLOW = "\u001B[33m"
        val ANSI_BLUE = "\u001B[34m"
        val ANSI_PURPLE = "\u001B[35m"
        val ANSI_CYAN = "\u001B[36m"
        val ANSI_WHITE = "\u001B[37m"
        val CHECK_MARK = "\u2713"
        val NEUTRAL_FACE = "\u0CA0_\u0CA0"
        val X_MARK = "\u274C"

        addTestListener(object: TestListener {
            val failures = ArrayList<String>()
            val skips = ArrayList<String>()

            override fun beforeSuite(suite: TestDescriptor?) {
                if(suite == null) {
                    return
                }

                if(suite.name.startsWith("Test Run") || suite.name.startsWith("Gradle Worker")) return

                if(suite.parent != null && suite.className != null) {
                    if(folding) {
                        println("##[group]" + suite.name + "\r")
                    }
                    println(ANSI_BOLD_WHITE + suite.name + ANSI_RESET)
                }

            }

            override fun afterTest(descriptor: TestDescriptor?, result: TestResult) {
                var indicator = ANSI_WHITE

                indicator = if(result.failedTestCount > 0) ANSI_RED + X_MARK
                else if(result.skippedTestCount > 0) ANSI_YELLOW + NEUTRAL_FACE
                else ANSI_GREEN + CHECK_MARK

                println("    " + indicator + ANSI_RESET + " " + descriptor?.name)

                if(result.failedTestCount > 0) {
                    println(" ")
                    failures.add("${descriptor?.parent}:${descriptor?.name}")
                }
                if(result.skippedTestCount > 0) {
                    skips.add("${descriptor?.parent}:${descriptor?.name}")
                }

            }

            override fun afterSuite(desc: TestDescriptor?, result: TestResult) {
                if(desc == null) {
                    return
                }

                if(desc.parent != null && desc.className != null) {
                    if(folding && result.failedTestCount == 0L) {
                        println("##[endgroup]\r")
                        println("")
                    }
                }

                // will match the outermost suite
                if(desc.parent == null) {
                    var failStyle = ANSI_RED
                    var skipStyle = ANSI_YELLOW

                    if(result.failedTestCount > 0) {
                        failStyle = ANSI_RED
                    }

                    if(result.skippedTestCount > 0) {
                        skipStyle = ANSI_YELLOW
                    }

                    val summaryStyle = when(result.resultType) {
                        TestResult.ResultType.SUCCESS -> ANSI_GREEN
                        TestResult.ResultType.FAILURE -> ANSI_RED
                        else -> ANSI_WHITE
                    }

                    println("--------------------------------------------------------------------------")
                    println(
                        "Results: " + summaryStyle + "${result.resultType}" + ANSI_RESET
                                + " (${result.testCount} tests, "
                                + ANSI_GREEN + "${result.successfulTestCount} passed" + ANSI_RESET
                                + ", " + failStyle + "${result.failedTestCount} failed" + ANSI_RESET
                                + ", " + skipStyle + "${result.skippedTestCount} skipped" + ANSI_RESET
                                + ")"
                    )

                    if(result.failedTestCount > 0) {
                        println(failStyle + "Failed tests:\n${failures.joinToString("\n") { " * $it "}}" + ANSI_RESET)
                    }

                    if(result.skippedTestCount> 0) {
                        println(skipStyle + "Skipped tests:\n${skips.joinToString("\n") { " * $it "}}" + ANSI_RESET)
                    }
                    println("--------------------------------------------------------------------------")
                }
            }

            override fun beforeTest(testDescriptor: TestDescriptor?) {
            }
        })
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
