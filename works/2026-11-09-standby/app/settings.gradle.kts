pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "standby"
include(":app")
// the StandBy app itself; :app is the measuring probe
include(":standby")
