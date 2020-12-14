package scenery

import gradle.kotlin.dsl.accessors._e98ba513b34f86980a981ef4cafb3d49.publishing

plugins {
    signing
}

// https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials
// save public and private key and passphrase into gradle.properties

signing {
    setRequired({ project.hasProperty("release") })
    sign(publishing.publications["maven"])
    sign(configurations.archives.get())
}