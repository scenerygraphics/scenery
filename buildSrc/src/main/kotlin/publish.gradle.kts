// configuration of the Maven artifacts
plugins {
    `maven-publish`
}
//apply plugin: 'maven-publish'

val sceneryName = "scenery"
val sceneryUrl = "http://scenery.graphics"

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "graphics.scenery"
            artifactId = sceneryName
            version = "0.7.0-beta-8-SNAPSHOT"

            from(components["java"])

            pom {
                name.set(sceneryName)
                description.set("flexible scenegraphing and rendering for scientific visualisation")
                url.set(sceneryUrl)
                properties.set(mapOf("inceptionYear" to "2016"))
                organization {
                    name.set(sceneryName)
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
                        url.set("https://imagej.net/User:Pietzsch")
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
                }
                mailingLists { mailingList { name.set("none") } }
                scm {
                    connection.set("scm:git:git://github.com/scenerygraphics/scenery")
                    developerConnection.set("scm:git:git@github.com:scenerygraphics/scenery")
                    tag.set("scenery-0.7.0-beta-7") // TODO differs from version
                    url.set(sceneryUrl)
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/scenerygraphics/scenery/issues")
                }
                ciManagement {
                    system.set("Travis")
                    url.set("https://travis-ci.org/scenerygraphics/scenery/")
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
            }
        }
    }
}

val ciao = 2
