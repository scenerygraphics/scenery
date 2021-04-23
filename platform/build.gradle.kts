plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

//println("from build-src build script: ${libs.versions.bb.get()}")
println("from build-src build script: ${sciJava.common.get()}")

dependencies {
    //    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    val catalogs = arrayOf(sciJava, imagej, imgLib2, scifio, fiji, bigDataViewer, trakEM2, n5, boneJ, ome, omero,
                           groovy, apache, batik, commons, eclipseCollections, eclipseSwt, googleCloud, jackson,
                           jetty, jGraphT, jna, jogamp, kotlib, logBack, migLayout, rSyntaxTextArea, slf4j,
                           snakeYAML, tensorFlow, junit5, jmh, misc)
    for (catalog in catalogs)
        implementation(files(catalog.javaClass.superclass.protectionDomain.codeSource.location))
}