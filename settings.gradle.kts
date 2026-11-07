pluginManagement {
    repositories {
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
    }
}

rootProject.name = "meyouww"

include(":app")
include(":core:model")
include(":core:scene")
include(":core:render")
include(":core:database")
include(":core:chat")
include(":core:alarm")
include(":core:designsystem")
include(":core:data")
include(":core:notifications")
include(":feature:onboarding")
include(":feature:home")
include(":feature:settings")
include(":feature:friends")
include(":feature:inbox")
include(":feature:studio")
include(":feature:chat")
include(":feature:alarms")
