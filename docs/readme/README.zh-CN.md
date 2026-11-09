<!-- translated-from: README.md @ 250b20e32093 -->
# Maeve

[English](../../README.md) · 简体中文 · [繁體中文](README.zh-Hant.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

**循环她来跑，主屏留给你。**

一个跑在安卓上的 AI Agent：在一块你看不见的屏幕上把事办完，主屏始终归你。

*Maeve 是独立维护的 Apache-2.0 项目。第三方代码与素材的署名见 [NOTICE](../../NOTICE)。*

*Maeve 与 HBO、Warner Bros. 或《西部世界》无关，也没有获得它们的背书。
这个名字只作为常见人名使用，指向剧中的设定。*

> *"Surviving is just another loop."* —— Maeve Millay，《西部世界》S1E7

版本代号：**Dolores** · `v0.1.0` · [SPEC.md](../../SPEC.md)

---

## 它能做什么

把要做的事告诉 Maeve。它开一块隐藏的后台屏，读屏幕、操作 App 和网页，直到事情办完。
你的主屏不会被占用。

- **手机还是你的。** 活在一块独立的虚拟屏上跑，你继续刷、继续看、继续打字。
- **随时插话、随时接管。** 跑到一半可以补充要求，也可以自己上手。
- **不可逆的事先问你。** 付款、删除、发送都要过确认门。
- **它会记住。** 偏好和重复的流程沉淀成记忆与技能，下次更快。

<details>
<summary>内置了使用知识的 App 和网站（没写到的照样能用）</summary>

<!-- generated:knowledge:start -->
<!-- 这一段由 ./gradlew updateReadme 生成，不要手改 -->
| 类别 | 内置 |
|---|---|
| **App** | Android 设置、百家号、哔哩哔哩、BOSS直聘、Chrome、得到、抖音、飞书、Gmail、即刻、快手、领英、QQ邮箱、Telegram、今日头条、Uber、Uber Eats、微信、微博、WhatsApp、闲鱼、小红书、知乎 |
| **网站** | Amazon、哔哩哔哩、Google Calendar、豆瓣、抖音、Instagram、Gmail、Google Maps、Telegram、百度贴吧、微博、搜狗微信、WhatsApp、小红书、小宇宙、YouTube、知乎、BOSS直聘 |
| **通用做法**（技能） | 改 Maeve 自己的设置、把内容整理成文字、处理 PDF 文档、查清一件事、消息与邮件、写技能 |
<!-- generated:knowledge:end -->

</details>

<details>
<summary>模型服务商预设</summary>

<!-- generated:providers:start -->
<!-- 这一段由 ./gradlew updateReadme 生成，不要手改 -->
内置 DeepSeek、通义千问、Kimi、智谱、智谱 Coding Plan、硅基流动、火山方舟、MiniMax、阶跃星辰、腾讯混元、百度千帆、OpenRouter、Requesty、OpenCode Go、Anthropic、Gemini、OpenAI、xAI、Mistral、Groq、Ollama、LM Studio 等服务商预设；其他服务商选「自定义」，OpenAI 兼容、Anthropic、Gemini、OpenAI Responses 接口都能接。
<!-- generated:providers:end -->

也可以不填 Key：用 ChatGPT（Plus / Pro）、xAI / Grok、OpenRouter 账号登录。
添加模型的步骤和各家的注意事项见[模型与登录](../models.md)。

</details>

## 两种用法

Maeve 只有一个执行内核，两种接任务的入口。两条路走同一个 Agent 循环和同一套守卫链——
远程任务不是另做一套、能力更弱的实现。

| 路线 | 入口 | 典型场景 |
|---|---|---|
| **R1 · 本机** | Maeve 自己的聊天界面 | 在手机上直接说；无障碍 + 后台虚拟屏把活干完。 |
| **R2 · 由另一个 Agent 驱动** | `/mcp` 上的入站 MCP，或某些系统级 Agent 运行时使用的本机文件桥 | 让电脑上的助手或手机上的 Agent 把任务派给手机，再读回结果。 |

R1 不需要任何配套设备。R2 是可选的，默认关闭；本机文件桥那层能力配置一次后就不再打扰。
两种远程通道**不能互相替代**：MCP 是网络服务，文件桥存在的原因是有些沙箱里的 Agent
运行时根本连不到手机的 loopback 端口。

## 使用条件

- 安卓 11（API 30）及以上。后台屏在安卓 14+ 上最稳。
- **一次性授权 ADB 或无线调试。** 虚拟屏由以 `shell` 身份运行的助手进程创建，读屏走无障碍服务。
  没有零权限方案。
- **你自己的模型服务商。** Maeve 在端侧执行，但规划需要模型；地址和 Key 在设置里配置。
  不内置任何模型权重。
- R2 可选：同一网络里的电脑，或 USB `adb forward`。

网络配置目前允许明文 HTTP——请使用可信的服务商和你能控制的网络。
不要把确认门当成全面的隐私保证。

## 从源码构建

```bash
git clone https://github.com/Daiyimo/Maeve.git
cd Maeve
./gradlew :app:assembleDebug
```

仓库有两个 Gradle 模块和一个内嵌的 Flutter 模块：

| 路径 | 是什么 |
|---|---|
| `:core` | 纯 JVM Kotlin。Agent 循环、工具、上下文、守卫、知识与技能。 |
| `:app` | 安卓宿主。无障碍、虚拟屏、shell 助手、WebView、沙箱、权限，以及仍在使用的 Compose 界面。 |
| `flutter/` | 正在替换 Compose 界面的 Flutter 外壳。`settings.gradle.kts` 没有把它列为 Gradle 模块；`:app` 使用它构建出的 debug AAR。 |

AAR 不存在时安卓宿主编不过：Flutter 模块构建会在 `flutter/` 下写出 `flutter-debug.aar`，
这个生成目录不进仓库。先构建 Flutter 模块，再汇编 App。
工具链、缓存和代理上的坑记录在 [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)。

发布签名、R8 和 CI 属于 [M4](../../SPEC.md#10-里程碑与验收) 的工作。

## 当前状态

仓库处于 **M2（交互层）**，界面正在从 Compose 迁到 Flutter 外壳。公开历史从一个
带 Apache-2.0 署名的基线快照开始，包名是 `com.daiyimo.maeve`，合规与署名层已经就位。
来龙去脉见 [ATTRIBUTION.md](../../ATTRIBUTION.md)。

完整的 M2 视觉系统、连接中枢与 bridge v3（M3）、发布加固（M4）都还没完成。
Flutter 外壳目前是工作台：回复是本地延时演示，对话和记忆只存在进程内存里，
安卓宿主也还没有注册对应的桥接处理器。[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)
列了每一条原型限制；这里没有任何已发布的二进制。

| 位置 | 内容 |
|---|---|
| [SPEC.md](../../SPEC.md) | 产品定义、架构、迁移、合规、里程碑 |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | Flutter 外壳、模块构建、工具链、限制与验证 |
| `docs/spec/` | 视觉 token、触感、bridge v3 协议、迁移记录 |
| `docs/adr/` | 架构决策记录 |
| `docs/dev/` | 架构、工程规范、review 记录、专题说明 |
| [ATTRIBUTION.md](../../ATTRIBUTION.md) | 来源、改了什么、Apache-2.0 §4 声明 |

## 参与贡献

见 [CONTRIBUTING.md](../../CONTRIBUTING.md)。提交贡献即表示同意以 Apache-2.0 授权
（inbound = outbound，§5）；上游项目没有 CLA，也没有 DCO。

## 许可证与署名

Apache-2.0 —— 见 [LICENSE](../../LICENSE)。`NOTICE` 按 Apache-2.0 原样保留上游署名。第三方与 GPL/LGPL 组件列在
[licenses/THIRD_PARTY_NOTICES.md](../../licenses/THIRD_PARTY_NOTICES.md)，并镜像进 APK。

建议的话题标签：`android` `ai-agent` `llm-agent` `accessibility-service` `virtual-display`
`kotlin` `jetpack-compose` `flutter` `apache-2.0`。
