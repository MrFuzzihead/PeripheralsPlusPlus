// Extra build configuration for the GTNH/RFG build.
//
// Peripherals++ ships a few third party libraries that are not available as mod dependencies at runtime.
// They are declared on the `embedded` configuration in dependencies.gradle and merged into the mod jar here.

configurations {
    embedded {
        canBeConsumed = false
        canBeResolved = true
    }
    // Make the embedded libraries available for compilation and for runClient/runServer, but do not publish them
    devOnlyNonPublishable.extendsFrom(embedded)
}

tasks.named("jar", Jar).configure { jar ->
    jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    jar.from({ configurations.embedded.files.collect { it.isDirectory() ? it : zipTree(it) } }) {
        exclude "META-INF/MANIFEST.MF"
        exclude "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA"
    }
}
