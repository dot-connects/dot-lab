plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Build output lives outside C:\DC: a sync tool there locks build/ and breaks incremental deletes.
subprojects {
    layout.buildDirectory.set(file("${System.getenv("LOCALAPPDATA")}/catchpet-build/${project.name}"))
}
