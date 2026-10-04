// Restart harness (Phase 2.5, see RestartHarness in src/gametest): three dedicated server runs
// in one directory. The buildscript defines the runs harnessSetup, harnessVerify and
// harnessVerifyReset; this plugin chains them, resets the world in between and checks each
// phase's report.

val harnessDir = layout.buildDirectory.dir("harness")

fun checkReport(phase: String) {
    val file = harnessDir.get().file("harness-report.properties").asFile
    if (!file.exists()) {
        throw GradleException("Harness phase '$phase' wrote no report ($file)")
    }
    val report = java.util.Properties().apply { file.inputStream().use { load(it) } }
    if (report.getProperty("phase") != phase || report.getProperty("passed") != "true") {
        throw GradleException("Harness phase '$phase' failed: ${report.getProperty("failures")} ($file)")
    }
    logger.lifecycle("Harness phase '$phase' passed")
}

val prepareHarness = tasks.register("prepareHarness") {
    group = "verification"
    description = "Creates an empty restart-harness server directory"
    doLast {
        val dir = harnessDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
        // Port 0: the OS picks a free port, so nodes can run in parallel
        dir.resolve("server.properties").writeText("online-mode=false\nserver-port=0\nspawn-protection=0\n")
    }
}

// A world reset as server owners do it: delete the overworld, nether and end, keep
// dimensions/ (islands) and data/ (registry, return positions, invitations)
val resetHarnessWorld = tasks.register("resetHarnessWorld") {
    group = "verification"
    description = "Deletes the harness world's overworld, nether and end"
    doLast {
        listOf("region", "entities", "poi", "DIM-1", "DIM1").forEach {
            harnessDir.get().dir("world/$it").asFile.deleteRecursively()
        }
    }
}

tasks.named { it == "runHarnessSetup" }.configureEach {
    dependsOn(prepareHarness)
    doLast { checkReport("setup") }
}
tasks.named { it == "runHarnessVerify" }.configureEach {
    dependsOn("runHarnessSetup")
    doLast { checkReport("verify") }
}
resetHarnessWorld.configure {
    dependsOn("runHarnessVerify")
}
tasks.named { it == "runHarnessVerifyReset" }.configureEach {
    dependsOn(resetHarnessWorld)
    doLast { checkReport("verify-reset") }
}

tasks.register("harnessTest") {
    group = "verification"
    description = "Islands, return positions and invitations survive a restart and a world reset"
    dependsOn("runHarnessVerifyReset")
}
