plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1" /* [SC] DO NOT EDIT */

// Register chiseled tasks for building all versions at once
stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "build"
    ofTask("build")
}

stonecutter registerChiseled tasks.register("chiseledTest", stonecutter.chiseled) {
    group = "verification"
    ofTask("test")
}

stonecutter registerChiseled tasks.register("chiseledPublishModrinth", stonecutter.chiseled) {
    group = "publishing"
    ofTask("modrinth")
}
