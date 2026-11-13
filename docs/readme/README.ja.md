<!-- translated-from: README.md @ 5f31dddc9fe2 -->
# Maeve

[English](../../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-Hant.md) · 日本語 · [한국어](README.ko.md) · [Español](README.es.md)

**ループは Maeve が回す。画面はあなたのもの。**

Android のための AI エージェント。あなたが見ることのない画面で仕事を片づけるので、
スマホはいつでもあなたのままです。

*Maeve は独立して保守されている Apache-2.0 プロジェクトです。第三者コードと素材の表示は [NOTICE](../../NOTICE) を参照してください。*

*Maeve は HBO、Warner Bros.、『ウエストワールド』とは関係がなく、推奨も受けていません。
この名前は一般的な人名として使い、作品の設定を指しています。*

> *"Surviving is just another loop."* — Maeve Millay, 『ウエストワールド』S1E7

リリースコードネーム：**Dolores** · `v0.1.0` · [SPEC.md](../../SPEC.md)

---

## できること

やってほしいことを伝えるだけ。Maeve は隠れたバックグラウンド画面を開き、画面を読み、
アプリとウェブを操作して仕事を終わらせます。メイン画面は奪いません。

- **スマホはあなたのまま。** 作業は別の仮想ディスプレイで進み、あなたはスクロールも視聴も入力も続けられます。
- **いつでも割り込み、いつでも引き継ぎ。** 実行中に要件を足せますし、自分で操作することもできます。
- **取り返しのつかないことは先に確認。** 支払い、削除、送信は確認ゲートを通ります。
- **覚えています。** 好みや繰り返す手順はメモリとスキルになり、次回は速くなります。

<details>
<summary>アプリとウェブサイトの内蔵ナレッジ（未収録のアプリも使えます）</summary>

一覧は `./gradlew updateReadme` が生成し、[英語 README](../../README.md) と
[簡体字中国語 README](README.zh-CN.md) の折りたたみにあります。

</details>

<details>
<summary>モデルプロバイダーのプリセット</summary>

プリセット一覧も `./gradlew updateReadme` が生成します。[英語 README](../../README.md) を参照してください。
キーを入れずに ChatGPT（Plus / Pro）、xAI / Grok、OpenRouter のアカウントでサインインすることもできます。

</details>

## 2 つの入口

Maeve の実行カーネルは 1 つで、タスクの受け口が 2 つあります。どちらも同じ Agent ループと
同じガードチェーンを通ります。リモートのタスクは、別に作られた弱い実装ではありません。

| 経路 | 入口 | 典型的な使い方 |
|---|---|---|
| **R1 · 端末内** | Maeve 自身のチャット UI | スマホで直接頼む。アクセシビリティとバックグラウンド仮想ディスプレイが仕事をする。 |
| **R2 · 別の Agent から駆動** | `/mcp` へのインバウンド MCP、または一部のシステム級 Agent ランタイムが使う端末内ファイルブリッジ | パソコンのアシスタントや端末上の Agent にタスクを任せ、結果を読み戻す。 |

R1 に相棒の端末は不要です。R2 は任意で既定はオフですが、端末内ファイルブリッジの層だけは
一度設定すればその後は邪魔をしません。2 つのリモート経路は**互換ではありません**。MCP は
ネットワークサービスで、ファイルブリッジはサンドボックス内の Agent ランタイムが
スマホの loopback ポートに届かない場合のためにあります。

## 必要なもの

- Android 11（API 30）以降。バックグラウンド画面は Android 14 以降が最も安定します。
- **ADB またはワイヤレスデバッグの一度きりの許可。** 仮想ディスプレイは `shell` 権限で動く
  ヘルパープロセスが作り、画面の読み取りはアクセシビリティサービス経由です。ゼロ権限の方法はありません。
- **自分の LLM プロバイダー。** Maeve は端末で実行しますが、計画にはモデルが必要です。
  エンドポイントとキーは設定で指定します。モデルの重みは同梱しません。
- R2 を使う場合のみ：同じネットワークのパソコン、または USB `adb forward`。

ネットワーク設定は今のところ平文 HTTP を許可しています。信頼できるエンドポイントと
自分で管理するネットワークを使ってください。確認ゲートを包括的なプライバシー保証と
みなさないでください。

## ソースからビルド

```bash
git clone https://github.com/Daiyimo/Maeve.git
cd Maeve
./gradlew :app:assembleDebug
```

リポジトリには Gradle モジュールが 2 つと、埋め込みの Flutter モジュールが 1 つあります。

| パス | 内容 |
|---|---|
| `:core` | 純粋な JVM Kotlin。Agent ループ、ツール、コンテキスト、ガード、ナレッジとスキル。 |
| `:app` | Android ホスト。アクセシビリティ、仮想ディスプレイ、shell ヘルパー、WebView、サンドボックス、権限、および現役の Compose 画面。 |
| `flutter/` | Compose UI を置き換えつつある Flutter シェル。`settings.gradle.kts` は Gradle モジュールとして含めず、`:app` がビルド済みの debug AAR を取り込みます。 |

AAR がないと Android ホストはコンパイルできません。Flutter モジュールのビルドが
`flutter/` の下に `flutter-debug.aar` を書き出し、生成ディレクトリはリポジトリに入りません。
先に Flutter モジュールをビルドしてからアプリを組み立ててください。ツールチェーン、キャッシュ、プロキシの落とし穴は
[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) に記録されています。

リリース署名、R8、CI は [M4](../../SPEC.md#10-里程碑与验收) の作業です。

## 現在の状態

リポジトリは **M2（インタラクション層）** にあり、UI は Compose から Flutter シェルへ
移行中です。公開履歴は Apache-2.0 の表示を含む単一のベースラインスナップショットから始まり、
パッケージ名は `com.daiyimo.maeve`、コンプライアンスと表示の層は整っています。Maeve は Mika（Apache-2.0）の派生著作物であり、上流の表示は [NOTICE](../../NOTICE) と [ATTRIBUTION.md](../../ATTRIBUTION.md) に保持されています。
経緯は [ATTRIBUTION.md](../../ATTRIBUTION.md) を参照してください。

M2 の視覚システム全体、接続ハブと bridge v3（M3）、リリース強化（M4）は未完了です。
Flutter シェルはワークベンチで、返信はローカルの遅延デモ、会話とメモリはプロセスメモリだけ、
Android ホストはブリッジハンドラーをまだ登録していません。
[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) に原型の制限がすべて並んでいます。
ここにはリリース済みのバイナリはありません。

| 場所 | 内容 |
|---|---|
| [SPEC.md](../../SPEC.md) | 製品定義、アーキテクチャ、移行、コンプライアンス、マイルストーン |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | Flutter シェル、モジュールビルド、ツールチェーン、制限と検証 |
| `docs/spec/` | ビジュアルトークン、ハプティクス、bridge v3 プロトコル、移行メモ |
| `docs/adr/` | アーキテクチャ決定記録 |
| `docs/dev/` | アーキテクチャ、エンジニアリング標準、レビュー記録、個別メモ |
| [ATTRIBUTION.md](../../ATTRIBUTION.md) | 来歴、変更点、Apache-2.0 §4 の声明 |

## コントリビューション

[CONTRIBUTING.md](../../CONTRIBUTING.md) を参照してください。コントリビュートすると、
Apache-2.0（inbound = outbound、§5）でライセンスすることに同意したものとみなされます。
上流プロジェクトには CLA も DCO もありません。

## ライセンスと表示

Apache-2.0 — [LICENSE](../../LICENSE) を参照してください。Maeve は Wang Zan の **Mika**（Apache-2.0）の派生著作物です。Apache-2.0 §4(d) の要求どおり、上流の表示は `NOTICE` に付録として保持されます。第三者および GPL/LGPL コンポーネントは
[licenses/THIRD_PARTY_NOTICES.md](../../licenses/THIRD_PARTY_NOTICES.md) に列挙され、APK にも同梱されます。

推奨トピック：`android` `ai-agent` `llm-agent` `accessibility-service` `virtual-display`
`kotlin` `jetpack-compose` `flutter` `apache-2.0`。
