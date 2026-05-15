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
        maven("https://jitpack.io")
    }
}

rootProject.name = "CurSimple"

include(
    ":app",
    ":core-kernel",
    ":core-data",
    ":core-plugin",
    ":core-reminder",
    ":feature-schedule",
    ":feature-plugin",
    ":feature-widget",
)
