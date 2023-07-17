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
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/scenerygraphics/scenery/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }
    dokkaJavadoc {
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/scenerygraphics/scenery/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
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
                archiveClassifier.set("javadoc")
            }

            val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
                dependsOn(tasks.dokkaHtml)
                from(tasks.dokkaHtml.flatMap { it.outputDirectory })
                archiveClassifier.set("html-doc")
            }


            artifact(dokkaJavadocJar)
            artifact(dokkaHtmlJar)
            // TODO, resolved dependencies versions? https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:resolved_dependencies

            pom {
                name.set(rootProject.name)
                description.set(rootProject.description)
                url.set(sceneryUrl)
                properties.set(mapOf("inceptionYear" to "2016"))
                organization {
                    name.set(rootProject.name)
                    url.set(sceneryUrl)
                }
                licenses {
                    license {
                        name.set("GNU Lesser General Public License v3+")
                        url.set("https://www.gnu.org/licenses/lgpl.html")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("skalarproduktraum")
                        name.set("Ulrik Guenther")
                        url.set("https://ulrik.is/writing")
                        roles.addAll("founder", "lead", "developer", "debugger", "reviewer", "support", "maintainer")
                    }
                }
                contributors {
                    contributor {
                        name.set("Kyle Harrington")
                        url.set("http://www.kyleharrington.com")
                        properties.set(mapOf("id" to "kephale"))
                    }
                    contributor {
                        name.set("Tobias Pietzsch")
                        url.set("https://imagej.net/people/tpietzsch")
                        properties.set(mapOf("id" to "tpietzsch"))
                    }
                    contributor {
                        name.set("Loic Royer")
                        properties.set(mapOf("id" to "royerloic"))
                    }
                    contributor {
                        name.set("Martin Weigert")
                        properties.set(mapOf("id" to "maweigert"))
                    }
                    contributor {
                        name.set("Aryaman Gupta")
                        properties.set(mapOf("id" to "aryaman-gupta"))
                    }
                    contributor {
                        name.set("Curtis Rueden")
                        url.set("https://imagej.net/people/ctrueden")
                        properties.set(mapOf("id" to "ctrueden"))
                    }
                }
                mailingLists { mailingList { name.set("none") } }
                scm {
                    connection.set("scm:git:https://github.com/scenerygraphics/scenery")
                    developerConnection.set("scm:git:git@github.com:scenerygraphics/scenery")
                    tag.set(if(snapshot) "HEAD" else "scenery-${rootProject.version}")
                    url.set(sceneryUrl)
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/scenerygraphics/scenery/issues")
                }
                ciManagement {
                    system.set("GitHub Actions")
                    url.set("https://github.com/scenerygraphics/scenery/actions")
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
