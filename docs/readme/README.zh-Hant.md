<!-- translated-from: README.md @ 587bea3f5e22 -->
# Maeve

[English](../../README.md) · [简体中文](README.zh-CN.md) · 繁體中文 · [日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md)

**迴圈她來跑，主螢幕留給你。**

一個跑在 Android 上的 AI Agent：在一塊你看不見的螢幕上把事情辦完，主螢幕始終歸你。

*Maeve 是獨立維護的 Apache-2.0 專案。第三方程式碼與素材的署名見 [NOTICE](../../NOTICE)。*

*Maeve 與 HBO、Warner Bros. 或《西方極樂園》無關，也沒有獲得它們的背書。
這個名字只作為常見人名使用，指向劇中的設定。*

> *"Surviving is just another loop."* —— Maeve Millay，《西方極樂園》S1E7

版本代號：**Dolores** · `v0.1.0` · [SPEC.md](../../SPEC.md)

---

## 它能做什麼

把要做的事告訴 Maeve。它開一塊隱藏的背景螢幕，讀螢幕、操作 App 和網頁，直到事情辦完。
你的主螢幕不會被佔用。

- **手機還是你的。** 工作在另一塊虛擬螢幕上跑，你繼續滑、繼續看、繼續打字。
- **隨時插話、隨時接管。** 跑到一半可以補充要求，也可以自己接手。
- **不可逆的事先問你。** 付款、刪除、傳送都要過確認門。
- **它會記住。** 偏好和重複的流程沉澱成記憶與技能，下次更快。

<details>
<summary>內建使用知識的 App 與網站（沒寫到的照樣能用）</summary>

完整清單由 `./gradlew updateReadme` 產生，放在[英文 README](../../README.md) 與
[簡體中文 README](README.zh-CN.md) 的「它能做什麼」折疊段裡。

</details>

<details>
<summary>模型服務商預設</summary>

預設清單同樣由 `./gradlew updateReadme` 產生，見[英文 README](../../README.md)。
也可以不填 Key：用 ChatGPT（Plus / Pro）、xAI / Grok、OpenRouter 帳號登入。

</details>

## 兩種用法

Maeve 只有一個執行核心，兩種接任務的入口。兩條路走同一個 Agent 迴圈和同一套守衛鏈——
遠端任務不是另做一套、能力更弱的實作。

| 路線 | 入口 | 典型場景 |
|---|---|---|
| **R1 · 本機** | Maeve 自己的聊天介面 | 在手機上直接說；無障礙 + 背景虛擬螢幕把事情做完。 |
| **R2 · 由另一個 Agent 驅動** | `/mcp` 上的入站 MCP，或某些系統級 Agent 執行時使用的本機檔案橋 | 讓電腦上的助理或手機上的 Agent 把任務派給手機，再讀回結果。 |

R1 不需要任何配套裝置。R2 是選用的，預設關閉；本機檔案橋那層能力設定一次後就不再打擾。
兩種遠端通道**不能互相取代**：MCP 是網路服務，檔案橋存在的原因是有些沙箱裡的 Agent
執行時根本連不到手機的 loopback 埠。

## 使用條件

- Android 11（API 30）以上。背景螢幕在 Android 14+ 上最穩。
- **一次性授權 ADB 或無線偵錯。** 虛擬螢幕由以 `shell` 身分執行的助理程序建立，讀螢幕走無障礙服務。
  沒有零權限方案。
- **你自己的模型服務商。** Maeve 在裝置端執行，但規劃需要模型；位址和 Key 在設定裡配置。
  不內建任何模型權重。
- R2 選用：同一網路裡的電腦，或 USB `adb forward`。

網路設定目前允許明文 HTTP——請使用可信的服務商和你能控制的網路。
不要把確認門當成全面的隱私保證。

## 從原始碼建置

```bash
git clone https://github.com/Daiyimo/Maeve.git
cd Maeve
./gradlew :app:assembleDebug
```

倉庫有兩個 Gradle 模組和一個內嵌的 Flutter 模組：

| 路徑 | 是什麼 |
|---|---|
| `:core` | 純 JVM Kotlin。Agent 迴圈、工具、上下文、守衛、知識與技能。 |
| `:app` | Android 宿主。無障礙、虛擬螢幕、shell 助理、WebView、沙箱、權限，以及仍在使用的 Compose 介面。 |
| `flutter/` | 正在替換 Compose 介面的 Flutter 外殼。`settings.gradle.kts` 沒有把它列為 Gradle 模組；`:app` 使用它建置出的 debug AAR。 |

AAR 不存在時 Android 宿主編不過：Flutter 模組建置會在 `flutter/` 下寫出 `flutter-debug.aar`，
這個生成目錄不進倉庫。先建置 Flutter 模組，再組譯 App。
工具鏈、快取和代理上的坑記錄在 [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)。

發佈簽章、R8 和 CI 屬於 [M4](../../SPEC.md#10-里程碑与验收) 的工作。

## 目前狀態

倉庫處於 **M2（互動層）**，介面正在從 Compose 遷到 Flutter 外殼。公開歷史從一個
帶 Apache-2.0 署名的基線快照開始，套件名是 `com.daiyimo.maeve`，合規與署名層已經就位。
來龍去脈見 [ATTRIBUTION.md](../../ATTRIBUTION.md)。

完整的 M2 視覺系統、連線中樞與 bridge v3（M3）、發佈加固（M4）都還沒完成。
Flutter 外殼目前是工作台：回覆是本地延時示範，對話和記憶只存在程序記憶體裡，
Android 宿主也還沒有註冊對應的橋接處理器。[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)
列了每一條原型限制；這裡沒有任何已發佈的二進位。

| 位置 | 內容 |
|---|---|
| [SPEC.md](../../SPEC.md) | 產品定義、架構、遷移、合規、里程碑 |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | Flutter 外殼、模組建置、工具鏈、限制與驗證 |
| `docs/spec/` | 視覺 token、觸感、bridge v3 協定、遷移記錄 |
| `docs/adr/` | 架構決策記錄 |
| `docs/dev/` | 架構、工程規範、review 記錄、專題說明 |
| [ATTRIBUTION.md](../../ATTRIBUTION.md) | 來源、改了什麼、Apache-2.0 §4 聲明 |

## 參與貢獻

見 [CONTRIBUTING.md](../../CONTRIBUTING.md)。提交貢獻即表示同意以 Apache-2.0 授權
（inbound = outbound，§5）；上游專案沒有 CLA，也沒有 DCO。

## 授權與署名

Apache-2.0 —— 見 [LICENSE](../../LICENSE)。`NOTICE` 原樣保留
`Mika / Copyright 2026 wangzan101`。第三方與 GPL/LGPL 元件列在
[licenses/THIRD_PARTY_NOTICES.md](../../licenses/THIRD_PARTY_NOTICES.md)，並鏡像進 APK。

建議的話題標籤：`android` `ai-agent` `llm-agent` `accessibility-service` `virtual-display`
`kotlin` `jetpack-compose` `flutter` `apache-2.0`。
