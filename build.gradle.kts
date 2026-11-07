import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
    version = libs.versions.mod.version.get()
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

val modsFolder1 = file("C:/Users/rdh/Desktop/mmc-develop-win32/MultiMC/instances/thisistnothi14/.minecraft/mods")
val modsFolder2 = file("C:/Users/rdh/Desktop/mmc-develop-win32/MultiMC/instances/1.21.4/.minecraft/mods")

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
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

val copyJarToInstance1 = tasks.register<Copy>("copyJarToInstance1") {
    dependsOn(jarTask)
    from(jarTask.map { it.outputs.files.singleFile })
    into(modsFolder1)
    rename { "swarm-improved.jar" }
    onlyIf { modsFolder1.exists() }
}

val copyJarToInstance2 = tasks.register<Copy>("copyJarToInstance2") {
    dependsOn(jarTask)
    from(jarTask.map { it.outputs.files.singleFile })
    into(modsFolder2)
    rename { "swarm-improved.jar" }
    onlyIf { modsFolder2.exists() }
}
