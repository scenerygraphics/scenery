package scenery

plugins {
    jacoco
}

tasks {

    // https://docs.gradle.org/current/userguide/java_testing.html#test_filtering
    test {
        // apparently `testLotsOfProteins` needs also a lot of heap..
        maxHeapSize = "8G"
        // [Debug] before running every test, prints out its name
        //        beforeTest(closureOf<TestDescriptor?> { logger.lifecycle("Running test: $this") })
        val gpuPresent = project.properties["gpu"]?.toString()?.toBoolean() == true
        //        println("gpuPresent=$gpuPresent")
        if (!gpuPresent) {
            filter { excludeTestsMatching("ExampleRunner") }
        } else {
            systemProperty("scenery.Renderer", "VulkanRenderer")
            systemProperty("scenery.ExampleRunner.OutputDir", "screenshots")
        }
        finalizedBy(jacocoTestReport) // report is always generated after tests run
    }

    register("testGpu", Test::class) { // lets take this for comfortability in local development
        maxHeapSize = "8G"
        group = "verification"
        filter { includeTestsMatching("ExampleRunner") }
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
            xml.isEnabled = true
            html.apply {
                isEnabled = false
                //                destination = file("$buildDir/jacocoHtml")
            }
        }
        dependsOn(test) // tests are required to run before generating the report
    }

    register<JavaExec>("run") {
        classpath = sourceSets.test.get().runtimeClasspath
        if(project.hasProperty("example")) {
            val example = project.property("example")
            val file = sourceSets.test.get().allSource.files.first { "class $example" in it.readText() }
            main = file.path.substringAfter("kotlin${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".kt")
            val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }
            allJvmArgs = allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + "-Xmx16g"
            //        println(classpath)
            //        println(example)
            //        println(file)
            //        println(main)
            //        println(allJvmArgs)
        }
    }
}

val TaskContainer.jacocoTestReport: TaskProvider<JacocoReport>
    get() = named<JacocoReport>("jacocoTestReport")

val TaskContainer.test: TaskProvider<Test>
    get() = named<Test>("test")

val Project.sourceSets: SourceSetContainer get() =
    (this as ExtensionAware).extensions.getByName("sourceSets") as SourceSetContainer

val SourceSetContainer.test: NamedDomainObjectProvider<SourceSet>
    get() = named<SourceSet>("test")