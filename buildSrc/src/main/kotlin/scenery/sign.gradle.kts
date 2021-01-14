package scenery

plugins {
    signing
    publishing
}

// https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials
// save public and private key and passphrase into gradle.properties

signing {
//    setRequired({ project.hasProperty("release") })
    useGpgCmd()
    sign(publishing.publications["maven"])
    sign(configurations.archives.get())
}

tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("release") }
}

