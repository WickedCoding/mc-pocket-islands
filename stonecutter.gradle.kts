plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1" /* [SC] DO NOT EDIT */

// Aggregate tasks that run a task for every version (names kept for CI workflows)
tasks.register("chiseledBuild") {
    group = "build"
    dependsOn(stonecutter.tasks.named("build"))
}

tasks.register("chiseledTest") {
    group = "verification"
    dependsOn(stonecutter.tasks.named("test"))
}

tasks.register("chiseledPublishModrinth") {
    group = "publishing"
    dependsOn(stonecutter.tasks.named("modrinth"))
}
