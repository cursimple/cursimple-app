pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Class-Schedule-Viewer"

include(
    ":app",
    ":core-kernel",
    ":core-js",
    ":core-data",
    ":feature-schedule",
    ":feature-widget",
)

