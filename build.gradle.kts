plugins {
    id("fabric-loom") version "1.15.3"
    id("maven-publish")
    id("com.modrinth.minotaur") version "2.+"
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    maven("https://maven.nucleoid.xyz/") {
        name = "Nucleoid"
    }
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("personalworlds") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    // Minecraft and mappings
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Fantasy — runtime dimension creation (included in JAR)
    modImplementation(include("xyz.nucleoid:fantasy:${property("fantasy_version")}")!!)

    // fabric-permissions-api — optional soft dependency for LuckPerms integration
    // NOT bundled: users install LuckPerms which provides a compatible version
    // Falls back to vanilla OP levels when no permission plugin is installed
    modCompileOnly("me.lucko:fabric-permissions-api:0.3.3")

    // Testing — JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testing — Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
}

val minecraft_version: String by project

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraft_version)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraft_version
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jar {
    inputs.property("archivesName", base.archivesName)

    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = property("archives_base_name") as String
            from(components["java"])
        }
    }
}

// Modrinth publishing configuration
modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("pocket-islands")
    versionNumber.set("${property("mod_version")}+${property("minecraft_version")}")
    versionType.set("release")
    uploadFile.set(tasks.remapJar)

    // Game version from Stonecutter context
    gameVersions.add(property("minecraft_version") as String)
    loaders.add("fabric")

    dependencies {
        required.project("fabric-api")
        // Fantasy is embedded in JAR via include() - no external dependency needed
    }

    // Changelog from environment variable (extracted from CHANGELOG.md in CI)
    val changelogContent = System.getenv("RELEASE_CHANGELOG")
    changelog.set(
        if (changelogContent.isNullOrBlank())
            "See [GitHub release](https://github.com/WickedSik/pocket-islands/releases) for full changelog."
        else
            changelogContent
    )

    // Sync project description from README
    syncBodyFrom.set(rootProject.file("README.md").readText())
}

tasks.modrinth {
    dependsOn(tasks.remapJar)
}
