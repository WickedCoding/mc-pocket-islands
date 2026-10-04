pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Downloads a missing JDK for the per-version Java toolchain
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "pocketislands"

stonecutter {
    kotlinController = true

    create(rootProject) {
        // Nodes are named <mc>-<loader> and built by build.<loader>.gradle.kts
        fun match(version: String, vararg loaders: String) = loaders.forEach {
            version("$version-$it", version).buildscript("build.$it.gradle.kts")
        }

        match("1.20.1", "fabric", "forge")
        match("1.20.4", "fabric")
        match("1.21.11", "fabric", "neoforge")

        vcsVersion = "1.20.1-fabric" // Active node for VCS (commit with this active)
    }
}
