plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 关于页上的构建编号:当前提交的短哈希;没有 git(如从源码包编译)时写 dev
val gitHash: String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "dev"

android {
    namespace = "com.daiyimo.maeve"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daiyimo.maeve"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dolores"
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

    buildTypes {
        // 真机验收时并排安装,保留发行版和用户数据;默认构建标识不变。
        if (providers.gradleProperty("devInstall").isPresent) getByName("debug") {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Maeve Dev")
        }
        // 看流畅度用的包:和发布版一样不可调试(调试包不做预先编译,界面慢好几倍,手感不作数),
        // 用本机调试签名,能直接覆盖调试包、保留数据;可被性能工具采样。./gradlew :app:assemblePerf
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isProfileable = true
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Flutter-first UI migration: the new shell is opt-in via FlutterShellActivity.
    implementation(files("../flutter/.android/Flutter/build/outputs/aar/flutter-debug.aar"))
    debugImplementation(files("G:/gradle-home/caches/modules-2/files-2.1/com.getkeepsafe.relinker/relinker/1.4.5/222d7b0624248da9336bbfccff8a129e3e280ce8/relinker-1.4.5.aar"))
    debugImplementation(files("G:/gradle-home/caches/modules-2/files-2.1/androidx.window/window/1.2.0/1d080d0a72684227bf6eb9af56264542708f8d16/window-1.2.0.aar"))
    debugImplementation(files("G:/gradle-home/caches/modules-2/files-2.1/androidx.window/window-java/1.2.0/924d0a3ca93fac4acb32c6a972e9fa1e283f1f07/window-java-1.2.0.aar"))
    debugImplementation(files(
        "G:/gradle-home/caches/modules-2/files-2.1/io.flutter/flutter_embedding_debug/1.0.0-deb287481e3ce9468f3434937ced4240a70539ca/2820d8d41717cfbffa60d2fc734a43ab0a8b8580/flutter_embedding_debug-1.0.0-deb287481e3ce9468f3434937ced4240a70539ca.jar",
        "G:/gradle-home/caches/modules-2/files-2.1/io.flutter/arm64_v8a_debug/1.0.0-deb287481e3ce9468f3434937ced4240a70539ca/5231e7ca773fc3789c114eca038c7d9a12135db9/arm64_v8a_debug-1.0.0-deb287481e3ce9468f3434937ced4240a70539ca.jar",
        "G:/gradle-home/caches/modules-2/files-2.1/io.flutter/armeabi_v7a_debug/1.0.0-deb287481e3ce9468f3434937ced4240a70539ca/d3e987c4ef55591d0edcb1912af12a4d15519806/armeabi_v7a_debug-1.0.0-deb287481e3ce9468f3434937ced4240a70539ca.jar",
        "G:/gradle-home/caches/modules-2/files-2.1/io.flutter/x86_64_debug/1.0.0-deb287481e3ce9468f3434937ced4240a70539ca/20c335ddbc5c860bbd6c0248a012c0c969cb5e0b/x86_64_debug-1.0.0-deb287481e3ce9468f3434937ced4240a70539ca.jar"
    ))
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

// LicensesTest 读仓库的 licenses/THIRD_PARTY_NOTICES.md:让它参与测试的增量判断
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("licenses/THIRD_PARTY_NOTICES.md")).withPropertyName("thirdPartyNotices")
}
