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
    maven("https://maven.parchmentmc.org") {
        name = "ParchmentMC"
    }
    mavenCentral()
}

// Loader implementations live in platform/<loader>/; this build compiles only the Fabric one
sourceSets.main {
    java.exclude("**/platform/forge/**", "**/platform/neoforge/**")
}

// In-game tests (GameTest server), kept out of the release jar. Compiled against main.
val gametest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    java.exclude("**/platform/forge/**", "**/platform/neoforge/**")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("personalworlds") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }

    mods {
        create("personalworlds-gametest") {
            sourceSet(gametest)
        }
    }

    // Fixed dev names: offline UUIDs derive from the name, so a random name per launch
    // would be a new player (and a new island) every time. Dev2 is the invite-test player.
    runs {
        create("client2") {
            inherit(getByName("client"))
            configName = "Minecraft Client 2"
            runDir("run/client2")
            programArgs("--username", "Dev2")
        }
        named("client") {
            programArgs("--username", "Dev")
        }

        // Headless GameTest server: runs every test, writes JUnit XML, exits with the failure count
        create("gametest") {
            server()
            configName = "Game Test"
            source(gametest)
            runDir("build/gametest")
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${layout.buildDirectory.file("gametest/junit.xml").get().asFile}")
        }
    }
}

dependencies {
    // Minecraft and mappings
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${property("minecraft_version")}:${property("parchment_version")}@zip")
    })
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

tasks.named<ProcessResources>("processGametestResources") {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraft_version)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraft_version
        )
    }

    exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")
}

// Per-version Java (17 for 1.20.x, 21 for 1.21.x). The toolchain also drives
// runClient/runServer, so they no longer depend on the system default JDK.
val javaVersion = (property("java_version") as String).toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
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

    from(rootProject.file("LICENSE")) {
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
            "See [GitHub release](https://github.com/WickedCoding/mc-pocket-islands/releases) for full changelog."
        else
            changelogContent
    )

    // Sync project description from README
    syncBodyFrom.set(rootProject.file("README.md").readText())
}

tasks.modrinth {
    dependsOn(tasks.remapJar)
}

// Each GameTest run starts from an empty world: islands from a previous run would be
// restored from the registry and change what the tests see
tasks.named("runGametest") {
    doFirst {
        delete(layout.buildDirectory.dir("gametest/world"))
    }
}
