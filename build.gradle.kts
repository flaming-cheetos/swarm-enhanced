import java.time.Instant
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.fabric.loom)
}

// Stamp the version with the exact commit used for this build.
val buildCommit = try {
    val head = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
    head.ifBlank { "local" }
} catch (e: Exception) {
    "local"
}

val buildVersion = "${libs.versions.mod.version.get()}+$buildCommit"
val buildTime = Instant.now().toString()

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    version = buildVersion
    group = providers.gradleProperty("maven_group").get()
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    // Meteor
    implementation(libs.meteor.client)

    // Fabric API (DataResourceStore / resource-loader types)
    implementation(libs.fabric.api)

    // Baritone for compile-time only (Meteor provides runtime). Use Meteor's published baritone.
    compileOnly(libs.baritone)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

// The H5 probe is a plain Java main, not a JUnit suite. Keeping it out of the
// normal test source set avoids changing the project's dependency set.
val h5Test = sourceSets.create("h5Test") {
    java.srcDir("src/h5Test/java")
    // Include main's compile dependencies (Gson is used by the panel JSON test).
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

val modsFolder1 = file("C:/Users/rdh/Desktop/mmc-develop-win32/MultiMC/instances/thisistnothi14/.minecraft/mods")
val modsFolder2 = file("C:/Users/rdh/Desktop/mmc-develop-win32/MultiMC/instances/1.21.4/.minecraft/mods")

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
            "commit" to buildCommit,
            "buildVersion" to buildVersion,
            "built" to buildTime
        )

        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
        filesMatching("build-info.properties") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}

val jarTask = tasks.named("jar")

// Absolute MMC paths are opt-in only; normal builds never copy into a live instance.
val copyToMmc = providers.gradleProperty("copyToMmc").map { it.toBoolean() }.orElse(false)

val copyJarToInstance1 = tasks.register<Copy>("copyJarToInstance1") {
    dependsOn(jarTask)
    from(jarTask.map { it.outputs.files.singleFile })
    into(modsFolder1)
    rename { "swarm-improved.jar" }
    onlyIf { copyToMmc.get() && modsFolder1.exists() }
}

val copyJarToInstance2 = tasks.register<Copy>("copyJarToInstance2") {
    dependsOn(jarTask)
    from(jarTask.map { it.outputs.files.singleFile })
    into(modsFolder2)
    rename { "swarm-improved.jar" }
    onlyIf { copyToMmc.get() && modsFolder2.exists() }
}

// H5 regression probes. Plain Java mains, no test-framework dependency.
val h5RecoveryTest = tasks.register<JavaExec>("h5RecoveryTest") {
    group = "verification"
    description = "Verify an orphaned ACTIVE task is recovered and redispatched"
    dependsOn(tasks.named("h5TestClasses"))
    classpath = sourceSets["h5Test"].runtimeClasspath
    mainClass.set("com.rdh.swarm.TaskQueueRecoveryTest")
    workingDir = layout.buildDirectory.dir("h5-recovery-test").get().asFile
    doFirst { workingDir.mkdirs() }
}

val h5LeaseTest = tasks.register<JavaExec>("h5LeaseTest") {
    group = "verification"
    description = "Verify lease claim, renewal, expiry, and host maintenance tick"
    dependsOn(tasks.named("h5TestClasses"))
    classpath = sourceSets["h5Test"].runtimeClasspath
    mainClass.set("com.rdh.swarm.LeaseLifecycleTest")
    workingDir = layout.buildDirectory.dir("h5-lease-test").get().asFile
    doFirst { workingDir.mkdirs() }
}

val h5FencingTest = tasks.register<JavaExec>("h5FencingTest") {
    group = "verification"
    description = "Verify stale-worker fencing, reconnect fresh leases, and exactly-once completion"
    dependsOn(tasks.named("h5TestClasses"))
    classpath = sourceSets["h5Test"].runtimeClasspath
    mainClass.set("com.rdh.swarm.FencingReconnectTest")
    workingDir = layout.buildDirectory.dir("h5-fencing-test").get().asFile
    doFirst { workingDir.mkdirs() }
}

val h5PanelTest = tasks.register<JavaExec>("h5PanelTest") {
    group = "verification"
    description = "Verify panel lease JSON omits the fencing token and both panels render its diagnostics"
    dependsOn(tasks.named("h5TestClasses"))
    classpath = sourceSets["h5Test"].runtimeClasspath
    mainClass.set("com.rdh.swarm.PanelLeaseJsonTest")
    workingDir = layout.buildDirectory.dir("h5-panel-test").get().asFile
    doFirst { workingDir.mkdirs() }
}

val h5ProtocolTest = tasks.register<JavaExec>("h5ProtocolTest") {
    group = "verification"
    description = "Verify the old/new host-worker compatibility matrix"
    dependsOn(tasks.named("h5TestClasses"))
    classpath = sourceSets["h5Test"].runtimeClasspath
    mainClass.set("com.rdh.swarm.ProtocolMatrixTest")
    workingDir = layout.buildDirectory.dir("h5-protocol-test").get().asFile
    doFirst { workingDir.mkdirs() }
}

val h5ProgressTest = tasks.register<JavaExec>("h5ProgressTest") {
    group = "verification"
    description = "Verify fenced progress is clamped and survives completion as 2/2"
    dependsOn(tasks.named("h5TestClasses"))
    classpath = sourceSets["h5Test"].runtimeClasspath
    mainClass.set("com.rdh.swarm.ProgressAccountingTest")
    workingDir = layout.buildDirectory.dir("h5-progress-test").get().asFile
    doFirst { workingDir.mkdirs() }
}

tasks.named("check") {
    dependsOn(h5RecoveryTest, h5LeaseTest, h5FencingTest, h5PanelTest, h5ProtocolTest, h5ProgressTest)
}
