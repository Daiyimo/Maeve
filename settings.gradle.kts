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
        maven {
            url = uri("flutter/build/host/outputs/repo")
            content { includeGroup("com.daiyimo.maeve_flutter") }
        }
        maven {
            val storageUrl = System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"
            url = uri("$storageUrl/download.flutter.io")
            content { includeGroup("io.flutter") }
        }
    }
}
rootProject.name = "Maeve"
include(":core", ":app")

// Flutter-first UI migration: the host consumes the verified local Flutter AAR.
