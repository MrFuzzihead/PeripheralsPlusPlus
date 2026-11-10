// Extra build configuration for the GTNH/RFG build.
//
// Peripherals++ ships a few third party libraries that are not available as mod dependencies at runtime.
// They are declared on the `embedded` configuration in dependencies.gradle and merged into the mod jar here.
//
// The test harness (JaCoCo, the Java 8 unit-test launcher, the in-game `gameTest` source set and the
// `-PsmokeTest` boot hook) also lives here. It mirrors the setup in MrFuzzihead/OpenPeripheral.

import java.math.BigDecimal

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Attaches the JaCoCo agent to a forked game.
 *
 * <p>
 * The `Test` tasks get the agent from the jacoco plugin; a `JavaExec` run task needs it as an explicit
 * `-javaagent` argument. The agent jar is declared as a `@Classpath` input so Gradle resolves it with the task (in
 * configuration-cache terms this is a task input, not a configuration-time resolution).
 * </p>
 */
abstract class JacocoAgentArgumentProvider : CommandLineArgumentProvider {

    @get:Classpath
    abstract val agent: ConfigurableFileCollection

    @get:Input
    abstract val destFile: Property<String>

    @get:Input
    abstract val includes: Property<String>

    override fun asArguments(): Iterable<String> {
        val jar = extractAgent()
        return listOf(
            "-javaagent:" + jar.absolutePath +
                "=destfile=" + destFile.get() +
                ",append=false,dumponexit=true" +
                ",includes=" + includes.get()
        )
    }

    /**
     * The `jacocoAgent` configuration resolves to the JaCoCo *distribution* jar, which has no `Premain-Class` of its
     * own: the real agent is `jacocoagent.jar` inside it. Gradle's own JaCoCo support unpacks it the same way.
     */
    private fun extractAgent(): java.io.File {
        val distribution = agent.files.single()
        val target = java.io.File(destFile.get()).parentFile.resolve("jacocoagent.jar")
        java.util.zip.ZipFile(distribution).use { zip ->
            val entry = zip.getEntry("jacocoagent.jar") ?: return distribution
            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
        return target
    }
}

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
tasks.withType<JavaExec>().configureEach {
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

    tasks.named<Jar>("jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(embeddedLibraries) {
            exclude("META-INF/MANIFEST.MF")
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Unit tests + coverage
// ---------------------------------------------------------------------------------------------------------

apply(plugin = "jacoco")

project.extensions.configure(JacocoPluginExtension::class.java) {
    toolVersion = "0.8.16"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isEnabled = true
    }
    // The mod targets Java 8 bytecode; run the suite on the same JVM generation the mod supports. Mockito 4's
    // inline mock maker also behaves best there.
    javaLauncher.set(
        project.extensions.getByType(JavaToolchainService::class.java).launcherFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    )
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Tests need the compileOnly dependencies too: several production classes implement compileOnly supertypes
// (cofh.api.energy, AE2, Forestry), so a test cannot even name the class without them on its classpath.
val mainSourceSet = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
configurations.named("testCompileClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}
configurations.named("testRuntimeClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}

// Only the unit test run feeds the report. A gameTestServer run also writes jacoco/gameTest.exec (see the agent
// below), but in a Forge 1.7.10 *dev* launch the LaunchClassLoader round-trips every class through ASM, so the class
// ids in that file cannot match the class files on disk: measured, 112 of 121 classes are rewritten. Merging it would
// only produce "Classes in bundle do not match with execution data" warnings and no data.
// CODE_REVIEW.md section 25 records the measurement and what would be needed to use it.
val jacocoExecFiles = project.files(project.layout.buildDirectory.file("jacoco/test.exec"))

fun jacocoClassDirs() = project.files(mainSourceSet.output.classesDirs).asFileTree.matching {
    // Generated version holder, rendering code, the accessor-only mixins and the vendored gtranslate library
    // are not meaningfully unit-testable headless; measuring them would only dilute the number.
    exclude(
        "com/austinv11/peripheralsplusplus/Tags.class",
        "com/austinv11/peripheralsplusplus/client/**",
        "com/austinv11/peripheralsplusplus/mixins/**",
        "com/austinv11/peripheralsplusplus/thirdparty/**"
    )
}

tasks.named<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates HTML and XML JaCoCo coverage reports from the unit test run"
    dependsOn(tasks.named("test"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    executionData.setFrom(jacocoExecFiles)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Fails the build when unit test coverage drops below the configured floor"
    dependsOn(tasks.named("test"))

    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
    executionData.setFrom(jacocoExecFiles)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                // This is a regression guard at the current baseline, not a target. The mod ships 6207 measurable
                // lines and the suite currently covers ~38.9% line / 31.1% branch. CODE_REVIEW.md section 24 has the
                // measured ceiling analysis: ~50% is the honest headless target, and most of the rest needs the game.
                minimum = BigDecimal("0.38")
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.30")
            }
        }
    }
}

// `check` runs the tests and enforces the floor, so CI catches coverage regressions.
tasks.named("check").configure {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// ---------------------------------------------------------------------------------------------------------
// In-game test mod (never shipped)
// ---------------------------------------------------------------------------------------------------------

val gameTestSourceSet = project.extensions.getByType(SourceSetContainer::class.java).create("gameTest")

configurations.getByName(gameTestSourceSet.compileClasspathConfigurationName)
    .extendsFrom(configurations.getByName("compileClasspath"))
configurations.getByName(gameTestSourceSet.runtimeClasspathConfigurationName)
    .extendsFrom(configurations.getByName("runtimeClasspath"))
gameTestSourceSet.compileClasspath += mainSourceSet.output
gameTestSourceSet.compileClasspath += mainSourceSet.compileClasspath
gameTestSourceSet.runtimeClasspath += mainSourceSet.output
gameTestSourceSet.runtimeClasspath += mainSourceSet.runtimeClasspath
// The game-test mod is plain Java 8. Leaving Jabel's compiler plugin on this source set's classpath makes javac
// auto-load it through ServiceLoader, which then needs ByteBuddy's agent (not present here).
gameTestSourceSet.compileClasspath = gameTestSourceSet.compileClasspath
    .filter { !it.name.contains("jabel") }

val isGameTestRun = project.hasProperty("gameTest") ||
    gradle.startParameter.taskNames.any { it.contains("gameTestServer") }

if (isGameTestRun) {
    // Only our package is instrumented, and the file is dumped when the game calls System.exit (the game test's
    // exitJava with halt=false), so a crashed or hung run simply leaves no data behind.
    // The data is NOT merged into jacocoTestReport: a dev launch rewrites every class through ASM, so the class ids
    // cannot match the files on disk (CODE_REVIEW.md section 25). It is produced so that a run without the dev
    // transformers - a production launcher - can be merged by pointing executionData at it.
    val jacocoProvider = objects.newInstance(JacocoAgentArgumentProvider::class.java)
    jacocoProvider.agent.from(configurations.named("jacocoAgent"))
    jacocoProvider.destFile.set(
        layout.buildDirectory.file("jacoco/gameTest.exec")
            .map { it.asFile.absolutePath }
    )
    jacocoProvider.includes.set("com.austinv11.peripheralsplusplus.*")

    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runServer") || name.startsWith("runClient")) {
            classpath += gameTestSourceSet.output
            systemProperty("peripheralsplusplus.gameTest", "true")
            // A reused dev world may reference blocks from mods that are no longer installed; the game test is not
            // about that, so answer FML's "continue anyway?" prompt up front instead of blocking on stdin.
            systemProperty("fml.queryResult", "confirm")
            jvmArgumentProviders.add(jacocoProvider)
        }
    }
}

tasks.named("check").configure {
    dependsOn(gameTestSourceSet.classesTaskName)
}

// ---------------------------------------------------------------------------------------------------------
// In-game coverage report
// ---------------------------------------------------------------------------------------------------------
// `gameTestServer` runs with the JaCoCo agent attached and writes build/jacoco/gameTest.exec. That data cannot be
// merged into jacocoTestReport: the Forge LaunchClassLoader round-trips every class through ASM, so the on-disk class
// files have different JaCoCo ids (measured: 112 of 121 classes differ, CODE_REVIEW.md section 25).
//
// The GTNH boot layer (RFB) already dumps each defined class under <run dir>/RFB_CLASS_DUMP/<loader>/, and those are
// the bytes JaCoCo saw - verified by comparing class ids. Reporting against them gives the in-game number.
val inGameDump = project.file(
    "${findProperty("runServerWorkingDirectory") ?: "run/server"}/RFB_CLASS_DUMP/Launch"
)

tasks.register<JacocoReport>("jacocoGameTestReport") {
    group = "verification"
    description = "Reports coverage from the in-game game test run, using the post-transform classes FML loaded"
    val execFile = project.layout.buildDirectory.file("jacoco/gameTest.exec").get().asFile
    // No `onlyIf`: a script closure cannot be stored in the configuration cache. An empty execution-data collection
    // makes JaCoCo skip the task instead, which is the same outcome.
    executionData.setFrom(
        project.files(execFile).filter { it.exists() }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(project.layout.buildDirectory.file("reports/jacoco/gameTest/jacocoGameTestReport.xml"))
        html.outputLocation.set(project.layout.buildDirectory.dir("reports/jacoco/gameTest/html"))
    }

    // The dump carries every loader's classes; only the game's own are ours.
    classDirectories.setFrom(
        project.files(inGameDump).asFileTree.matching {
            include("com/austinv11/peripheralsplusplus/**")
            exclude(
                "com/austinv11/peripheralsplusplus/Tags.class",
                "com/austinv11/peripheralsplusplus/client/**",
                "com/austinv11/peripheralsplusplus/mixins/**",
                "com/austinv11/peripheralsplusplus/thirdparty/**",
                // The in-game test mod itself is not part of the mod.
                "com/austinv11/peripheralsplusplus/gametest/**"
            )
        }
    )
    sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
}

tasks.register("gameTestServer") {
    group = "verification"
    description = "Boots a dedicated server, runs the in-game game test mod, and exits with its result"
    if (isGameTestRun && tasks.names.contains("runServer25")) dependsOn(tasks.named("runServer25"))
    finalizedBy(tasks.named("jacocoGameTestReport"))
}

// ---------------------------------------------------------------------------------------------------------
// Modern-JVM run aliases
// ---------------------------------------------------------------------------------------------------------
// ComputerCraft 3.3.x declares `required-after:lwjgl3ify`, which needs a modern JVM. GTNHGradle's plain
// runServer/runClient use the Java 8 toolchain and carry no lwjgl3ify on the classpath, so they die at
// MissingModsException before the game starts. Forward them to the modern variants.
mapOf("runServer" to "runServer25", "runClient" to "runClient25").forEach { (plain, modern) ->
    tasks.matching { it.name == plain }.configureEach {
        if (tasks.names.contains(modern)) {
            description = "Alias for $modern - ComputerCraft 3.3.x requires lwjgl3ify, which needs Java 17+"
            dependsOn(modern)
            onlyIf("the plain task forwards to $modern") { false }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Headless smoke test
// ---------------------------------------------------------------------------------------------------------
// `./gradlew runServer25 -PsmokeTest` makes the forked game exit itself once FML reports load complete, so CI
// can boot a real server without an operator. See SmokeTestHook.
if (project.hasProperty("smokeTest")) {
    val smokeTestDelay = (findProperty("smokeTestDelay") as String?) ?: "2000"
    tasks.withType<JavaExec>().configureEach {
        systemProperty("peripheralsplusplus.smokeTest", "true")
        systemProperty("peripheralsplusplus.smokeTest.delay", smokeTestDelay)
    }
}
