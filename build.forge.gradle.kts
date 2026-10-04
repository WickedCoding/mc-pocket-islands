plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.148"
    id("moddev-mutex")
    id("maven-publish")
    id("com.modrinth.minotaur") version "2.+"
    id("pocketislands-harness")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

val minecraft_version: String by project
val forge_version: String by project

repositories {
    maven("https://maven.parchmentmc.org") {
        name = "ParchmentMC"
    }
    exclusiveContent {
        forRepository { maven("https://maven.commoble.net/") { name = "Commoble" } }
        filter { includeGroup("commoble.infiniverse") }
    }
    mavenCentral()
}

// Loader implementations live in platform/<loader>/; this build compiles only the Forge one
sourceSets.main {
    java.exclude("**/platform/fabric/**", "**/platform/neoforge/**")
}

// In-game tests (GameTest server), kept out of the release jar. Compiled against main.
val gametest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    java.exclude("**/platform/fabric/**", "**/platform/neoforge/**")
}

// Tests are loader-neutral and need only Minecraft classes, not a running FML.
// legacyforge has no unitTest support, so reuse the main classpaths.
sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

legacyForge {
    version = "$minecraft_version-$forge_version"

    parchment {
        minecraftVersion = minecraft_version
        mappingsVersion = property("parchment_version") as String
    }

    // The gametest source set joins the mod in dev runs so FML scans its @GameTestHolder classes
    mods {
        register("personalworlds") {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }

    // Fixed dev names, as on Fabric: offline UUIDs derive from the name.
    // Dev2 is the invite-test player.
    runs {
        register("client") {
            client()
            programArguments.addAll("--username", "Dev")
        }
        register("client2") {
            client()
            gameDirectory = file("run/client2")
            programArguments.addAll("--username", "Dev2")
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
        // Restart harness (pocketislands-harness plugin): one dedicated server run per phase
        for ((run, phase) in listOf("harnessSetup" to "setup", "harnessVerify" to "verify", "harnessVerifyReset" to "verify-reset")) {
            register(run) {
                server()
                sourceSet = gametest
                gameDirectory = file("build/harness")
                programArgument("--nogui")
                systemProperty("pocketislands.harness", phase)
            }
        }

        // Headless GameTest server: runs every test, exits with the failure count
        register("gametest") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = file("build/gametest")
            systemProperty("forge.enabledGameTestNamespaces", "personalworlds")
            systemProperty("pocketislands.gametest.report-file", layout.buildDirectory.file("gametest/junit.xml").get().asFile.absolutePath)
        }
    }
}

dependencies {
    // Infiniverse — runtime dimension creation. modImplementation remaps the SRG jar for
    // dev; jarJar bundles the original jar (as Fantasy is bundled on Fabric)
    val infiniverse = "commoble.infiniverse:infiniverse-1.20.1:${property("infiniverse_version")}"
    modImplementation(infiniverse)
    jarJar(infiniverse) {
        version { strictly("[${property("infiniverse_version")},)"); prefer(property("infiniverse_version") as String) }
    }

    // MixinExtras (@WrapOperation, @Local) — Fabric Loader ships it, Forge 47 does not,
    // so it is bundled with jarJar
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.5")!!)
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:0.5.5") {
        version { strictly("[0.5.5,)"); prefer("0.5.5") }
    })

    // Mixin annotation processor: writes the refmap that maps the mixins' Mojang names
    // to SRG names for production
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    // Testing — JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testing — Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
}

mixin {
    add(sourceSets.main.get(), "pocketislands.refmap.json")
    config("pocketislands.mixins.json")
}

tasks.named<ProcessResources>("processGametestResources") {
    exclude("fabric.mod.json")
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "loader" to "forge",
        "loader_version_range" to "[47,)",
        "minecraft_version_range" to "[1.20.1,1.20.2)"
    )
    inputs.properties(props)

    filesMatching("META-INF/mods.toml") {
        expand(props)
    }

    // Loom handles the refmap on Fabric; only the Forge copy points at one
    inputs.property("refmap", "pocketislands.refmap.json")
    filesMatching("pocketislands.mixins.json") {
        filter { line -> line.replace("\"required\": true,", "\"required\": true,\n  \"refmap\": \"pocketislands.refmap.json\",") }
    }

    exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

// Per-version Java (17 for 1.20.x, 21 for 1.21.x)
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

    // Forge 1.20.1 loads mixin configs from the manifest, not from mods.toml
    manifest.attributes("MixinConfigs" to "pocketislands.mixins.json")

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
    // Loader suffix: the Fabric build of the same MC version is "<mod>+<mc>"
    versionNumber.set("${property("mod_version")}+$minecraft_version-forge")
    versionType.set("release")
    // The reobfuscated (SRG) jar; the dev jar in build/devlibs only runs in a dev environment
    uploadFile.set(tasks.named("reobfJar"))

    gameVersions.add(minecraft_version)
    loaders.add("forge")

    // Infiniverse and MixinExtras are bundled with jarJar - no external dependency needed

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
    dependsOn(tasks.named("reobfJar"))
}

// Each GameTest run starts from an empty world: islands from a previous run would be
// restored from the registry and change what the tests see
tasks.named("runGametest") {
    doFirst {
        delete(layout.buildDirectory.dir("gametest/world"))
    }
}
