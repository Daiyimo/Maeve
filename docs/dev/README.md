# 开发文档

简体中文 | [English](README.en.md)

写给想读代码、改代码、补知识的人。怎么构建、怎么提 PR，先看[贡献指南](../../CONTRIBUTING.md)。用户文档在[上一级](../README.md)。

## 先读

| 文档 | 讲什么 |
| --- | --- |
| [architecture.md](architecture.md) | 按代码现状写的整体结构，带类名和文件路径：主循环、工具、手机操作、浏览器、沙箱、电脑连接、知识与技能 |
| [project-standards.md](project-standards.md) | 工程规范：模块边界、目录与命名、提交格式、文档规则、验证门禁 |
| [project-review.md](project-review.md) | 2026-11-09 的深度 review：问题、证据、优先级和处理结果 |
| [roadmap.md](roadmap.md) | 已完成、接下来、已知问题与暂缓 |

## Flutter 外壳

| 文档 | 讲什么 |
| --- | --- |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | 迁移方向、模块构建顺序、工具链与缓存、原型限制、验证方式 |
| [flutter/README.md](../../flutter/README.md) | 模块内目录、命令与桥接契约 |

## 写知识和技能

| 文档 | 讲什么 |
| --- | --- |
| [knowledge-format.md](knowledge-format.md)（[English](knowledge-format.en.md)） | 内置 App 知识（`APP.md`）、网站知识（`SITE.md`）和技能（`SKILL.md`）的格式 |
| [skill-scripts.md](skill-scripts.md) | 知识和技能自带的脚本怎么放、在沙箱里怎么运行 |

## 界面与体验

| 文档 | 讲什么 |
| --- | --- |
| [copy-rules.md](copy-rules.md) | 界面文案规范：标题、状态值、按钮、空状态和 `InfoTip` |
| [maeve-uiux-refactor.md](maeve-uiux-refactor.md) | Compose 时期的 UI/UX 重构记录：文件地图、设计系统、玻璃分层与验证结论 |
| [ios-development-standard.md](ios-development-standard.md) | iOS 端开发规范；Android 与 Flutter 的行为差异也记在这里 |

## 专题说明与验证记录

| 文档 | 讲什么 |
| --- | --- |
| [app-access.md](app-access.md) | 应用访问规则、主动分享授权与标准手机工具的限制边界 |
| [account-signin.md](account-signin.md) | 用账号登录模型服务商（不填 Key）的设计，以及明确不做的部分 |
| [chatgpt-signin-check.md](chatgpt-signin-check.md) | 上手机之前，在电脑上用 App 同一套代码走一遍「用 ChatGPT 登录」 |
| [doubao-asr.md](doubao-asr.md) | 豆包语音识别的接入方式与限制 |
| [amoo-bridge.md](amoo-bridge.md) | amoo 文件桥的目录信箱、协议与真机验证 |
| [amoo-connect.md](amoo-connect.md) | amoo 连接侧的一次性握手与技能注入 |
| [amoo-wechat.md](amoo-wechat.md) | 微信双向中转、Amoo 会话、执行开关与恢复行为 |
| [amoo-intercept.md](amoo-intercept.md) | amoo 拦截与接管链路的实现记录 |
| [remote-device-access-check.md](remote-device-access-check.md) | 远程亮屏与解锁的验证记录 |
| [remote-mcp.md](remote-mcp.md) | 自托管远程 CLI / MCP 的部署、手机鉴权和断线语义 |

## 仓库里别处的开发文档

- [`eval/README.md`](../../eval/README.md)：真机评测。
- [`tools/sandbox/README.md`](../../tools/sandbox/README.md)：沙箱原生程序和自带运行环境的重新构建。
- [`tools/relay/README.md`](../../tools/relay/README.md)：自托管远程 MCP 网关。
- [`tools/i18n/README.md`](../../tools/i18n/README.md)：10 种语言的译文检查。
- [`tooling/toolchain/README.md`](../../tooling/toolchain/README.md)：固定版本的 JDK / Gradle / AGP / Flutter 工具链，安装、自检与跨项目复用。

开发文档目前除知识格式外只有中文版；入口文档（README、CONTRIBUTING、FLUTTER_MIGRATION）有英文。
