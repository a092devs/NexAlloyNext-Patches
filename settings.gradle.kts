pluginManagement {
    repositories {
        maven(url = "https://raw.githubusercontent.com/a092devs/NexAlloyNext/maven")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info")
        // The NexAlloy patcher API, published by https://github.com/a092devs/NexAlloyNext.
        maven(url = providers.gradleProperty("nexalloyMaven").get())
    }
}

plugins {
    id("com.android.settings") version ("9.2.1")
}

android {
    compileSdk = 37
    minSdk = 27
}

rootProject.name = "NexAlloyNext-Patches"

include(":stub")
include(":bundles:nexfusion")
