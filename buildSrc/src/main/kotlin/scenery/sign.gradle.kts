package scenery

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

val Project.publishing: PublishingExtension get() =
    (this as ExtensionAware).extensions.getByName("publishing") as PublishingExtension