//package scenery
//
//import org.gradle.api.artifacts.dsl.ArtifactHandler
//import org.gradle.api.tasks.bundling.Jar
//import org.gradle.kotlin.dsl.getValue
//import org.gradle.kotlin.dsl.invoke
//import org.gradle.kotlin.dsl.provideDelegate
//import org.gradle.kotlin.dsl.register
//import java.net.URL
//
//plugins {
//    id("org.jetbrains.dokka")
//}
//
//tasks {
//    dokkaHtml {
//        dokkaSourceSets.configureEach {
//            sourceLink {
//                localDirectory.set(file("src/main/kotlin"))
//                remoteUrl.set(URL("https://github.com/scenerygraphics/scenery/tree/master/src/main/kotlin"))
//                remoteLineSuffix.set("#L")
//            }
//        }
//    }
//}
//
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
//artifacts {
//    archives(dokkaJavadocJar)
//    archives(dokkaHtmlJar)
//}
//
//fun ArtifactHandler.archives(artifactNotation: Any) =
//    add("archives", artifactNotation)