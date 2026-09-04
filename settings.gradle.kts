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

rootProject.name = "Resource Monitor"
include(":app")
include(":core:model")
include(":core:animation-contract")
include(":core:telemetry-api")
include(":core:telemetry-mock")
include(":core:telemetry-fusion")
include(":core:designsystem")
include(":animations:holographic-rings")
include(":core:telemetry-system")
include(":core:config")
 