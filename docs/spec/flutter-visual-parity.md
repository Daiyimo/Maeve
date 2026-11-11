# Flutter 界面与 Android 宿主边界

当前视觉契约见 [visual-tokens.md](visual-tokens.md)。Flutter 自己定义品牌、色彩、形状、字体、材质和动效，当前默认觉醒紫。

## 启动

所有启动器别名统一指向 `MainActivity`，它继承 FlutterActivity。旧 Flutter 组件名是同一宿主的兼容别名。重复的 Compose 应用 Activity、导航根和页面已从生产源码移除，不存在可切回旧首页的入口。通知和系统入口同样回到 Flutter。

## 保留的原生代码

Android 的任务服务、手机助手、浏览器能力、模型和账户状态、存储、通知与系统悬浮层继续存在。Compose 仅用于系统悬浮胶囊、画中画、原生文件预览等平台组件，不能充当应用首页。

原页面中仍被业务和平台组件使用的纯模型、文案、格式化与状态函数单独保留。没有为了删页面而删任务逻辑或放宽门禁。

## 测试迁移

旧 Compose 应用页面的 instrumentation 测试随页面退役；Flutter 的行为测试与全页面布局矩阵接替其界面职责。Android 业务单测、能力测试、原生浮层测试仍保留。任务恢复设备测试改用真正的 Flutter Activity 宿主。

`FlutterEntrypointTest` 检查清单里的统一入口和旧 Activity 缺席；`FlutterLaunchDeviceTest` 在真实设备上检查 FlutterView、首帧、重建以及启动器别名。

此前逐项复刻 Compose 的 [对照历史](../reference/compose-before-flutter/flutter-visual-parity.md) 仅供追溯。
