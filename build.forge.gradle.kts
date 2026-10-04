plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.148"
    id("moddev-mutex")
    id("maven-publish")
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

    mods {
        register("personalworlds") {
            sourceSet(sourceSets.main.get())
        }
    }

    // Fixed dev name, as on Fabric: offline UUIDs derive from the name
    runs {
        register("client") {
            client()
            programArguments.addAll("--username", "Dev")
        }
        register("server") {
            server()
            programArgument("--nogui")
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

    // Testing — JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testing — Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
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
