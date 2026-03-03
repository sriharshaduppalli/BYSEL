pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // No additional plugins declared here to avoid plugin resolution issues
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BYSEL"
include(":app")
