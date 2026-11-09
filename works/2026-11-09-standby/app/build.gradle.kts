plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("io.github.takahirom.roborazzi") version "1.29.0" apply false
}

// Build output lives outside C:\DC: a sync tool there locks build/ and breaks incremental deletes.
// SB_BUILD_DIR lets parallel worktrees build without sharing one output folder.
subprojects {
    val root = System.getenv("SB_BUILD_DIR") ?: "${System.getenv("LOCALAPPDATA")}/standby-build"
    layout.buildDirectory.set(file("$root/${project.name}"))
}
