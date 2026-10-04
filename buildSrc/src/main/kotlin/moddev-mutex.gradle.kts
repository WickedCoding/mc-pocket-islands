import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

// ModDevGradle recompiles Minecraft per node; doing that in parallel fills the disk, so
// allow one at a time. Covers both the NeoForge and legacyforge plugins (same task name).
interface ModDevMutex : BuildService<BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("createMinecraftArtifactsMutex", ModDevMutex::class.java) {
    maxParallelUsages.set(1)
}

tasks.named { it == "createMinecraftArtifacts" }.configureEach {
    usesService(mutex)
}
