/* This is free and unencumbered software released into the public domain */

import org.gradle.kotlin.dsl.provideDelegate

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "7.0.4" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.6" // Import Shadow plugin.
    id("checkstyle") // Import Checkstyle plugin.
    eclipse // Import Eclipse plugin.
}

/* --------------------------- JDK Config ------------------------------ */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain { // Select Java toolchain.
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

/* ----------------------------- Metadata ------------------------------ */
group = "com.lishid"

version = "4.4.9"

val apiVersion = "1.19"

/* ---------------------------- Repos ---------------------------------- */
allprojects {
    repositories {
        mavenCentral() // Import the Maven Central Maven Repository.
        gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
        maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
        maven { url = uri("https://hub.spigotmc.org/nexus/content/groups/public/") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("file://${System.getProperty("user.home")}/.m2/repository") }
        System.getProperty("SELF_MAVEN_LOCAL_REPO")?.let {
            val dir = file(it)
            if (dir.isDirectory) {
                println("Using SELF_MAVEN_LOCAL_REPO at: $it")
                maven { url = uri("file://${dir.absolutePath}") }
            } else {
                logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
                mavenLocal()
            }
        } ?: logger.error("TrueOG Bootstrap not found, defaulting to ~/.m2 for mavenLocal()")
    }
}

/* ---------------------- Java project deps ---------------------------- */
dependencies { implementation(project(":plugin")) }

/* -------------------------- Subprojects ------------------------------ */
project(":api") {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")

    dependencies {
        compileOnly("org.jetbrains:annotations:24.1.0")
        compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    }
}

project(":plugin") {
    apply(plugin = "java-library")
    apply(plugin = "eclipse")

    dependencies {
        implementation(project(":api"))
        compileOnly("org.jetbrains:annotations:24.1.0")
        compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
        implementation("com.github.jikoo:planarwrappers:3.2.3")
    }

    tasks.named<ProcessResources>("processResources") {
        val props = mapOf("version" to version, "apiVersion" to apiVersion)
        inputs.properties(props) // Indicates to rerun if version changes.
        filesMatching("plugin.yml") { expand(props) }
        from("${rootProject.projectDir}/LICENSE") { into("/") } // Bundle licenses into jarfiles.
    }
}

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    archiveBaseName.set("OpenInv")
    archiveClassifier.set("")
    minimize()
    exclude("META-INF/MANIFEST.MF")
    relocate("com.github.jikoo.planarwrappers", "com.github.jikoo.openinv.planarwrappers")
    dependencies {
        include(project(":plugin"))
        include(project(":api"))
        include(dependency("com.github.jikoo:planarwrappers:3.2.3"))
    }
}

tasks.jar { archiveClassifier.set("part") }

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) }

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters") // Enable reflection for java code.
    options.isFork = true // Run javac in its own process.
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    java {
        eclipse().configFile("config/formatter/eclipse-java-formatter.xml") // Eclipse java formatting.
        leadingTabsToSpaces() // Convert leftover leading tabs to spaces.
        removeUnusedImports() // Remove imports that aren't being called.
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

checkstyle {
    toolVersion = "10.18.1" // Declare checkstyle version to use.
    configFile = file("config/checkstyle/checkstyle.xml") // Point checkstyle to config file.
    isIgnoreFailures = true // Don't fail the build if checkstyle does not pass.
    isShowViolations = true // Show the violations in any IDE with the checkstyle plugin.
}

tasks.named("compileJava") {
    dependsOn("spotlessApply") // Run spotless before compiling with the JDK.
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}
