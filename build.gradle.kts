import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    java
    kotlin("jvm") version "1.3.30"
    idea
    id("fabric-loom") version "0.2.4-SNAPSHOT"
}

base {
    archivesBaseName = "betterportals"
}

val minecraft: String by ext
version = determineVersion()
group = "de.johni0702.minecraft"


allprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.getByName<ProcessResources>("processResources") {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(
                    mutableMapOf(
                            "version" to project.version
                    )
            )
        }
    }
}

minecraft {
}

inline fun DependencyHandler.modCompileAndInclude(str: String, block: ExternalModuleDependency.() -> Unit = {}) {
    modCompile(str, block)
    include(str, block)
}

dependencies {
    /**
     * Gets a version string with the [key].
     */
    fun v(key: String) = ext[key].toString()

    minecraft("com.mojang:minecraft:$minecraft")
    mappings("net.fabricmc:yarn:" + v("minecraft") + '+' + v("mappings"))

    // Fabric
    modCompile("net.fabricmc:fabric-loader:${v("fabric-loader")}")
    modCompile("net.fabricmc.fabric-api:fabric-api:${v("fabric-api")}")
    modCompile("net.fabricmc:fabric-language-kotlin:${v("fabric-kotlin")}")
    compileOnly("net.fabricmc:fabric-language-kotlin:${v("fabric-kotlin")}")

    // Other libraries
    compileOnly("org.apiguardian:apiguardian-api:1.0.0")
}

fun determineVersion(): String {
    val latestVersion = file("version.txt").readLines().first()
    val releaseCommit = command("git", "blame", "-p", "-l", "version.txt").first().split(' ').first()
    val currentCommit = command("git", "rev-parse", "HEAD").first()
    var version =
        if (releaseCommit == currentCommit) {
            latestVersion
        } else {
            val diff = command("git", "log", "--format=oneline", "$releaseCommit..$currentCommit").size
            "$latestVersion-$diff-g${currentCommit.substring(0, 7)}"
        }
    if (command("git", "status", "--porcelain").isNotEmpty()) {
        version = "$version*"
    }
    return version
}

fun command(vararg cmd: String): List<String> {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine(*cmd)
        standardOutput = stdout
    }
    return stdout.toString().split('\n')
}
