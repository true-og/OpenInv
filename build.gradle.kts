import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.paperweight.userdev.ReobfArtifactConfiguration
import io.papermc.paperweight.tasks.RemapJar

plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "8.3.9"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
    eclipse
}

group = "com.lishid"
version = "4.4.4-SNAPSHOT"

val apiVersion = "1.19"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.REOBF_PRODUCTION

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.purpurmc.org/snapshots")
}

dependencies {
    paperweight.paperDevBundle("1.19.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
}

sourceSets {
    main {
        java.setSrcDirs(
            listOf(
                "api/src/main/java",
                "plugin/src/main/java",
                "internal/v1_19_R3/src/main/java",
            ),
        )
        resources.setSrcDirs(listOf("plugin/src/main/resources"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.compilerArgs.add("-parameters")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    archiveBaseName.set("OpenInv")
}

tasks.processResources {
    val props = mapOf("version" to project.version, "apiVersion" to apiVersion)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
    from("LICENSE.txt") {
        into("/")
    }
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("dev-all")
    minimize()
}

tasks.named("assemble") {
    dependsOn(tasks.named("reobfJar"))
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<RemapJar>("reobfJar") {
    outputJar.set(layout.buildDirectory.file("libs/OpenInv-${project.version}.jar"))
}

eclipse {
    project {
        name = rootProject.name
    }
}
