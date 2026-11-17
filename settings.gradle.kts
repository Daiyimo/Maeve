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
        // Flutter 外壳的 AAR 是本项目自己编出来的,只存在于下面这几个本地仓库。
        // 必须排在公网仓库之前:AAR 声明成 isChanging(源码一改就要换新的二进制),每次构建都要重新解析,
        // 而 Gradle 遇到网络故障会直接中断、不会接着试下一个仓库 —— 公网在前时,离线或 DNS 不通
        // 就会让一个明明躺在本地的模块解析失败。
        for (mode in listOf("debug", "profile", "release")) {
            maven {
                url = uri(if (mode == "debug") "flutter/build/host/outputs/repo" else "flutter/build/host-$mode/host/outputs/repo")
                content { includeModule("com.daiyimo.maeve_flutter", "flutter_$mode") }
            }
        }
        google()
        mavenCentral()
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
