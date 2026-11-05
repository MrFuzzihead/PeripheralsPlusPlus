// Extra build configuration for the GTNH/RFG build.
//
// Peripherals++ ships a few third party libraries that are not available as mod dependencies at runtime.
// They are declared on the `embedded` configuration in dependencies.gradle and merged into the mod jar here.

val embedded: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// Make the embedded libraries available for compilation and for runClient/runServer, but do not publish them
configurations.named("devOnlyNonPublishable") {
    extendsFrom(embedded)
}

// CollectiveFramework's coremod patches a few vanilla classes (RenderEnchantmentTable, FontRenderer, GuiMainMenu,
// EntityAnimal/EntityAIMate) with a ClassWriter that only computes max stack, so the stack map frames it leaves
// behind are stale. That is harmless for the vanilla (Java 6) bytecode, but the RFG-recompiled dev Minecraft is
// Java 8 bytecode, where those frames must be valid -> VerifyError during startup. Disable bytecode verification
// for the dev runs as a workaround (still honoured by the JetBrains JDK 21 used by the modern run tasks).
tasks.withType<org.gradle.api.tasks.JavaExec>().configureEach {
    if (name.startsWith("runClient") || name.startsWith("runServer")) {
        jvmArgs("-noverify")
    }
}

// Resolved after evaluation (once the GTNH convention has finished setting up the configurations) and
// eagerly unpacked, so that no script references are captured by the jar task (configuration cache safe).
afterEvaluate {
    val embeddedLibraries = embedded.files.map { file ->
        if (file.isDirectory) file else zipTree(file)
    }

    tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(embeddedLibraries) {
            exclude("META-INF/MANIFEST.MF")
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        }
    }
}
