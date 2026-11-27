import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 关于页上的构建编号:当前提交的短哈希;没有 git(如从源码包编译)时写 dev
val gitHash: String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "dev"

// 发布签名:密钥不进仓库。按「local.properties → 环境变量」的顺序取。debug/perf 仍可使用调试签名;
// release 缺密钥时直接失败,避免产出看似正式、实际上不可升级也不可验证的包。属性名见 local.properties.example。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    (localProps.getProperty(prop) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("maeve.release.storeFile", "MAEVE_RELEASE_STORE_FILE")
val releaseStoreFile = releaseStorePath?.let { file(it) }?.takeIf { it.isFile }
val releaseStorePassword = signingValue("maeve.release.storePassword", "MAEVE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("maeve.release.keyAlias", "MAEVE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("maeve.release.keyPassword", "MAEVE_RELEASE_KEY_PASSWORD")
val hasReleaseKey = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null
val requireReleaseSigning by tasks.registering {
    doLast {
        if (!hasReleaseKey) throw GradleException("[maeve] 没有发布密钥，拒绝打包 release。请按 local.properties.example 配置密钥。")
    }
}
tasks.matching { it.name in setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle") }.configureEach {
    dependsOn(requireReleaseSigning)
}

android {
    namespace = "com.daiyimo.maeve"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daiyimo.maeve"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        // 只写「主.次.修订」,不带代号后缀:发布 tag 是 v0.1.0,而 AppVersion 把带后缀的当预发布版、
        // 判正式版更新 —— 写成 0.1.0-dolores 的话,装了 0.1.0 的人会被提示「升级到 0.1.0」,
        // 点安装又因 versionCode 相同被系统拒绝。代号(dolores)写在发布说明里。
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        // 关于页版本号后的 Beta 标记;出正式版时改成 false
        buildConfigField("boolean", "BETA", "true")
    }

    // 沙箱程序只有 arm64 版本(jniLibs/arm64-v8a);APK 不限 ABI,别的设备照常安装、显示「此设备不支持沙箱」。
    // 沙箱程序要从 nativeLibraryDir 直接执行,安装时必须解到磁盘
    packaging { jniLibs { useLegacyPackaging = true } }

    // 发布用的包(perf、release)按架构再出一份:除了全架构包,另出只带 arm64-v8a 原生库的小包。
    // 两个包代码、资源、版本号、签名都一样,只差 lib/ 下的其他架构;各自是完整 APK,不是一起安装的拆分包。
    // 产物:app-universal-<类型>.apk、app-arm64-v8a-<类型>.apk。拆分对所有构建类型生效,
    // 所以只在这次构建要出 perf/release 包时打开,单独构建调试包时产物仍是 app-debug.apk。
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { Regex("(?i)(perf|release)").containsMatchIn(it) }
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
    // 自带的运行环境(assets/runtime/rootfs.tar.xz,tools/sandbox/build-runtime.py 生成)已是 xz,不再压一遍
    androidResources { noCompress += "xz" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 评测任务集(仓库根目录 eval/tasks)打进测试包的 assets,真机评测跑器从那里读题
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("eval/tasks"))

    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            // 三种都开着,AGP 会按 minSdk 挑够用的:minSdk 30 时只写 v3(安卓 11 起都认),
            // 以后 minSdk 降下去会自动补上 v1/v2。v3 带签名历史,换密钥时能平滑升级。
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
        // 真机验收时并排安装,保留发行版和用户数据;默认构建标识不变。
        if (providers.gradleProperty("devInstall").isPresent) getByName("debug") {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Maeve Dev")
        }
        // 看流畅度用的包:和发布版一样不可调试(调试包不做预先编译,界面慢好几倍,手感不作数),
        // 用本机调试签名,能直接覆盖调试包、保留数据;可被性能工具采样。./gradlew :app:assemblePerf
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perf"
            versionNameSuffix = "-perf"
            resValue("string", "app_name", "Maeve Perf")
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        resValues = true
        compose = true
        aidl = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Flutter 外壳的 debug AAR 由这个任务生成,干净 clone 直接汇编 :app 也不必先手工跑 Flutter。
// 工具链从 FLUTTER_ROOT(或 PATH)来;版本和安装方式见 tooling/toolchain/README.md。
val flutterRootDir: String? = System.getenv("FLUTTER_ROOT")?.takeIf { it.isNotBlank() }
val isWindowsHost: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val flutterExecutable: String = if (flutterRootDir != null) {
    File(flutterRootDir, if (isWindowsHost) "bin/flutter.bat" else "bin/flutter").absolutePath
} else if (isWindowsHost) {
    "flutter.bat"
} else {
    "flutter"
}
// 每种 Android 变体只消费同模式 AAR；不同模式输出互不覆盖。
for ((mode, variant) in listOf("debug" to "Debug", "profile" to "Perf", "release" to "Release")) {
    val output = rootProject.file(if (mode == "debug") "flutter/build" else "flutter/build/host-$mode")
    val aar = tasks.register<Exec>("flutterBuild${variant}Aar") {
        group = "build"
        workingDir(rootProject.file("flutter"))
        commandLine(flutterExecutable, "build", "aar", "--$mode",
            *listOf("debug", "profile", "release").filter { it != mode }.map { "--no-$it" }.toTypedArray(),
            "--output", output.absolutePath)
        inputs.files(rootProject.fileTree("flutter/lib"), rootProject.fileTree("flutter/assets"),
            rootProject.file("flutter/pubspec.yaml"), rootProject.file("flutter/pubspec.lock"))
        inputs.property("flutterMode", mode)
        inputs.property("flutterOutputRoot", output.absolutePath)
        outputs.dir(File(output, "host/outputs/repo"))
    }
    if (!providers.gradleProperty("skipFlutterAar").isPresent) {
        tasks.matching { it.name == "pre${variant}Build" }.configureEach { dependsOn(aar) }
    }
}

// Resolve the generated POM and its transitive engine dependencies through Maven.
// A fresh CI runner must not depend on a developer's Gradle cache hashes.
dependencies {
    debugImplementation("com.daiyimo.maeve_flutter:flutter_debug:1.0") { isChanging = true }
    add("perfImplementation", "com.daiyimo.maeve_flutter:flutter_profile:1.0") { isChanging = true }
    releaseImplementation("com.daiyimo.maeve_flutter:flutter_release:1.0") { isChanging = true }
    implementation(project(":core"))
    implementation(libs.protobuf.lite)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    // 两种识别模型都随 APK 安装；首次使用不依赖 Play 服务下载。
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    // 微信绑定二维码只在本机编码，不调用在线图片服务。
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // 沙箱路径契约(core 的 testFixtures):AndroidSandbox 在 JVM 与真机上都跑同一套
    testImplementation(testFixtures(project(":core")))
    androidTestImplementation(testFixtures(project(":core")))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.junit)
}

// 本地模块使用固定坐标，但 AAR 会随源码改变，不能复用昨天缓存的二进制。
configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    // Compose 1.7.6 的测试依赖会传递旧 runner/JUnit；所有 source set 对齐显式版本，
    // 避免 AGP consistent resolution 把旧版本锁进 debugRuntimeClasspath。
    resolutionStrategy.eachDependency {
        if (requested.group == "androidx.test" && requested.name == "runner") {
            useVersion(libs.versions.androidxTestRunner.get())
        }
        if (requested.group == "junit" && requested.name == "junit") {
            useVersion(libs.versions.junit.get())
        }
    }
}

// LicensesTest 读仓库的 licenses/THIRD_PARTY_NOTICES.md:让它参与测试的增量判断
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("licenses/THIRD_PARTY_NOTICES.md")).withPropertyName("thirdPartyNotices")
}
