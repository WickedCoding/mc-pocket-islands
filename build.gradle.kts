plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
    id("maven-publish")
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

    // fabric-permissions-api — optional soft dependency for LuckPerms integration (included in JAR)
    // Falls back to vanilla OP levels when no permission plugin is installed
    // Exclude the fabric-api-bom to prevent it from overriding our fabric-api
    modImplementation(include("me.lucko:fabric-permissions-api:0.3.3") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api-bom")
    }!!)

    // Testing — JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
