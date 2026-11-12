# Amoo 接入页

入口：Flutter 设置 → 执行与设备 → **Amoo**。页面在 `flutter/lib/ui/settings/amoo_page.dart`，通过 `maeve/settings` 的 `amoo` 方法调用原生接入流程。

## 当前链路

Amoo 与 Maeve 通过 `/sdcard/Android/media/<包名>/amoo/` 的文件桥交换任务与结果，不以手机回环地址的 HTTP/MCP 连通性判断接入。早期文档中的“复制网关配置 / 现在自检”属于旧方案，已被文件桥方案替代。协议背景见 [amoo-bridge.md](amoo-bridge.md)。

- **开始接入**：检测到 StepOS 的 `com.stepos.ailauncher` 且 Maeve 无障碍服务正在运行时，调用既有 `AmooSetup.start()`。用户切到 Amoo 聊天后，原生流程填入并发送技能接入说明，然后等待文件桥结果验证。
- **接入进度**：读取 `AmooSetup.stage`，区分等待聊天页、发送中、验证中、完成和失败。页面可见且应用在前台时每两秒刷新，回前台也会刷新；离开页面停止轮询。
- **取消接入**：调用 `AmooSetup.cancel()`，只停止本次接入等待；开始接入时开启的文件桥会保留。
- **文件桥状态**：来自 `FeatureFlags.bridgeAmoo()`；最近结果取 `out/` 中实际 JSON 文件的修改时间，不读取结果正文。桥已开启不等于技能验证成功。

页面读取状态不会开始接入。未安装对应 Amoo 应用或无障碍未运行时，开始按钮不可用；原生调用也再次检查前置条件。应用查询只声明这个明确的目标包。无障碍入口打开 Android 系统设置，授权后返回刷新。

## 迁移修复

旧 Compose 页面在 Flutter 唯一入口迁移时移除，但真实 Amoo 后端仍保留。本轮恢复 Flutter 入口及其原生动作，删除旧页面遗留的无调用私有辅助函数。未恢复聊天任务截获开关；它与文件桥接入是不同能力，见 [amoo-intercept.md](amoo-intercept.md)。

组件回归覆盖入口可达、开始/取消、状态刷新、读取失败恢复、前置条件和平台消息解码；布局审计包含小屏、大字和宽屏。原生测试验证状态元数据与实际结果文件；设备测试确认读取状态不会启动接入。
