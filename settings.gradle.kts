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
        for (mode in listOf("debug", "profile", "release")) {
            maven {
                url = uri(if (mode == "debug") "flutter/build/host/outputs/repo" else "flutter/build/host-$mode/host/outputs/repo")
                content { includeModule("com.daiyimo.maeve_flutter", "flutter_$mode") }
            }
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
