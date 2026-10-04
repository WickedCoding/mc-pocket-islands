plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1-fabric" /* [SC] DO NOT EDIT */

stonecutter parameters {
    // Loader of this node (the part after the last '-'), usable as //? if fabric
    val loader = current.project.substringAfterLast('-')
    constants.match(loader, "fabric", "forge", "neoforge")

    // Mojang renamed these classes in 1.21.11. Sources use the pre-1.21.11 names;
    // Stonecutter rewrites them when switching nodes. Word boundaries keep mod names
    // such as IdentifierCompat and validateIdentifier untouched.
    replacements.regex(current.parsed >= "1.21.11") {
        replace("\\bResourceLocationException\\b" to "IdentifierException", "\\bIdentifierException\\b" to "ResourceLocationException")
        replace("\\bResourceLocation\\b" to "Identifier", "\\bIdentifier\\b" to "ResourceLocation")
        replace("\\bPortalInfo\\b" to "TeleportTransition", "\\bTeleportTransition\\b" to "PortalInfo")
    }
}

// Aggregate tasks that run a task for every node (names kept for CI workflows)
tasks.register("chiseledBuild") {
    group = "build"
    dependsOn(stonecutter.tasks.named("build"))
}

tasks.register("chiseledTest") {
    group = "verification"
    dependsOn(stonecutter.tasks.named("test"))
}

// In-game tests: a headless GameTest server per node (exits non-zero on a failed test)
tasks.register("chiseledGameTest") {
    group = "verification"
    dependsOn(stonecutter.tasks.named("runGametest"))
}

// Restart harness: islands, return positions and invitations survive restart and world reset
tasks.register("chiseledHarnessTest") {
    group = "verification"
    dependsOn(stonecutter.tasks.named("harnessTest"))
}

tasks.register("chiseledPublishModrinth") {
    group = "publishing"
    dependsOn(stonecutter.tasks.named("modrinth"))
}
