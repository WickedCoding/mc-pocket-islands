import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// ModDevGradle decompiles and recompiles Minecraft per node. Running that for several
// nodes in parallel filled the disk (Phase 0.2), so allow one at a time. Covers both
// the NeoForge and the legacyforge plugin, which use the same task name.
interface ModDevMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", ModDevMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
