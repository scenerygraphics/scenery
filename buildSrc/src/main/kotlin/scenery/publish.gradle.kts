package scenery

import java.net.URL

// configuration of the Maven artifacts
plugins {
    `maven-publish`
    id("org.jetbrains.dokka")
}

val sceneryUrl = "http://scenery.graphics"

val snapshot = rootProject.version.toString().endsWith("SNAPSHOT")

tasks {
    dokkaHtml {
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory = file("src/main/kotlin")
                remoteUrl = URL("https://github.com/scenerygraphics/scenery/tree/main/src/main/kotlin")
                remoteLineSuffix = "#L"
            }
        }
    }
    dokkaJavadoc {
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory = file("src/main/kotlin")
                remoteUrl = URL("https://github.com/scenerygraphics/scenery/tree/main/src/main/kotlin")
                remoteLineSuffix = "#L"
            }
        }
    }
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "graphics.scenery"
            artifactId = rootProject.name
            version = rootProject.version.toString()

            from(components["java"])

            val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
                dependsOn(tasks.dokkaJavadoc)
                from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
                archiveClassifier = "javadoc"
            }

            val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
                dependsOn(tasks.dokkaHtml)
                from(tasks.dokkaHtml.flatMap { it.outputDirectory })
                archiveClassifier = "html-doc"
            }


            artifact(dokkaJavadocJar)
            artifact(dokkaHtmlJar)
            // TODO, resolved dependencies versions? https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:resolved_dependencies

            pom {
                name = rootProject.name
                description = rootProject.description
                url = sceneryUrl
                inceptionYear = "2016"
                organization {
                    name = rootProject.name
                    url = sceneryUrl
                }
                licenses {
                    license {
                        name = "GNU Lesser General Public License v3+"
                        url = "https://www.gnu.org/licenses/lgpl.html"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "skalarproduktraum"
                        name = "Ulrik Guenther"
                        url = "https://ulrik.is/writing"
                        roles.addAll("founder", "lead", "developer", "debugger", "reviewer", "support", "maintainer")
                    }
                }
                contributors {
                    contributor {
                        name = "Kyle Harrington"
                        url = "http://www.kyleharrington.com"
                        properties = mapOf("id" to "kephale")
                    }
                    contributor {
                        name = "Tobias Pietzsch"
                        url = "https://imagej.net/people/tpietzsch"
                        properties = mapOf("id" to "tpietzsch")
                    }
                    contributor {
                        name = "Loic Royer"
                        properties = mapOf("id" to "royerloic")
                    }
                    contributor {
                        name = "Martin Weigert"
                        properties = mapOf("id" to "maweigert")
                    }
                    contributor {
                        name = "Aryaman Gupta"
                        properties = mapOf("id" to "aryaman-gupta")
                    }
                    contributor {
                        name = "Curtis Rueden"
                        url = "https://imagej.net/people/ctrueden"
                        properties = mapOf("id" to "ctrueden")
                    }
                }
                mailingLists { mailingList { name = "none" } }
                scm {
                    connection = "scm:git:https://github.com/scenerygraphics/scenery"
                    developerConnection = "scm:git:git@github.com:scenerygraphics/scenery"
                    tag = if(snapshot) "HEAD" else "scenery-${rootProject.version}"
                    url = sceneryUrl
                }
                issueManagement {
                    system = "GitHub Issues"
                    url = "https://github.com/scenerygraphics/scenery/issues"
                }
                ciManagement {
                    system = "GitHub Actions"
                    url = "https://github.com/scenerygraphics/scenery/actions"
                }
                distributionManagement {
                    // https://stackoverflow.com/a/21760035/1047713
                    //                    <snapshotRepository>
                    //                        <id>ossrh</id>
                    //                        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
                    //                    </snapshotRepository>
                    //                    <repository>
                    //                        <id>ossrh</id>
                    //                        <url>https://oss.sonatype.org/service/local/staging/deploy/maven2/</url>
                    //                    </repository>
                }

//                artifact("${rootProject.name}-${rootProject.version}-sources.jar")
//                artifact("${rootProject.name}-${rootProject.version}-javadoc.jar")
            }
        }
    }

    repositories.maven {
        name = "sonatype"
        credentials(PasswordCredentials::class)

        val releaseRepo = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
        val snapshotRepo = "https://oss.sonatype.org/content/repositories/snapshots/"

        url = uri(if (snapshot) snapshotRepo else releaseRepo)
        //            url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2")
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
