
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
//    jcenter() // or maven(url="https://dl.bintray.com/kotlin/dokka")
}

//, imagej, imgLib2, scifio, fiji, bigDataViewer, trakEM2, n5, boneJ, ome, omero,
//groovy, apache, batik, commons, eclipseCollections, eclipseSwt, googleCloud, jackson,
//jetty, jGraphT, jna, jogamp, kotlib, logBack, migLayout, rSyntaxTextArea, slf4j,
//snakeYAML, tensorFlow, junit5, jmh, misc

dependencies {
    implementation("com.github.johnrengelman:shadow:8.1.1")
}
