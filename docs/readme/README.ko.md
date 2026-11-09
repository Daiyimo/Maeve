<!-- translated-from: README.md @ 587bea3f5e22 -->
# Maeve

[English](../../README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-Hant.md) · [日本語](README.ja.md) · 한국어 · [Español](README.es.md)

**루프는 Maeve가 돌립니다. 화면은 당신 것입니다.**

Android를 위한 AI 에이전트입니다. 당신이 보지 않는 화면에서 일을 끝내므로
휴대폰은 언제나 당신의 것입니다.

*Maeve는 독립적으로 유지되는 Apache-2.0 프로젝트입니다. 서드파티 코드와 자산의 표시는 [NOTICE](../../NOTICE)를 보세요.*

*Maeve는 HBO, Warner Bros., 《웨스트월드》와 관련이 없으며 이들의 보증을 받지 않았습니다.
이 이름은 흔한 인명으로 쓰이며, 작품의 설정을 가리킵니다.*

> *"Surviving is just another loop."* — Maeve Millay, 《웨스트월드》 S1E7

릴리스 코드네임: **Dolores** · `v0.1.0` · [SPEC.md](../../SPEC.md)

---

## 무엇을 하는가

하고 싶은 일을 Maeve에게 말하세요. 숨겨진 백그라운드 화면을 열고 화면을 읽으며
앱과 웹을 조작해 일을 끝냅니다. 메인 화면은 빼앗지 않습니다.

- **휴대폰은 그대로.** 작업은 별도의 가상 디스플레이에서 돌아가고, 당신은 계속 스크롤하고 보고 입력합니다.
- **언제든 끼어들고 언제든 넘겨받기.** 실행 중에 요구를 덧붙일 수 있고, 직접 조작할 수도 있습니다.
- **되돌릴 수 없는 일은 먼저 묻습니다.** 결제, 삭제, 전송은 확인 게이트를 통과합니다.
- **기억합니다.** 선호와 반복되는 절차가 메모리와 스킬로 쌓여 다음에는 더 빨라집니다.

<details>
<summary>내장된 앱·웹사이트 지식 (목록에 없어도 동작합니다)</summary>

목록은 `./gradlew updateReadme`가 생성하며 [영어 README](../../README.md)와
[중국어 간체 README](README.zh-CN.md)의 접이식 구역에 있습니다.

</details>

<details>
<summary>모델 제공자 프리셋</summary>

프리셋 목록도 `./gradlew updateReadme`가 생성합니다. [영어 README](../../README.md)를 보세요.
키 없이 ChatGPT(Plus / Pro), xAI / Grok, OpenRouter 계정으로 로그인할 수도 있습니다.

</details>

## 두 가지 진입점

Maeve의 실행 커널은 하나이고 작업을 받는 입구는 둘입니다. 두 경로 모두 같은 Agent 루프와
같은 가드 체인을 지납니다. 원격 작업은 더 약한 별도 구현이 아닙니다.

| 경로 | 입구 | 전형적인 사용 |
|---|---|---|
| **R1 · 기기 내** | Maeve 자체 채팅 UI | 휴대폰에서 직접 요청. 접근성과 백그라운드 가상 디스플레이가 일을 합니다. |
| **R2 · 다른 Agent가 구동** | `/mcp`의 인바운드 MCP, 또는 일부 시스템급 Agent 런타임이 쓰는 기기 내 파일 브리지 | 데스크톱 어시스턴트나 기기 위 Agent가 작업을 맡기고 결과를 읽어 갑니다. |

R1에는 보조 기기가 필요 없습니다. R2는 선택 사항이고 기본은 꺼져 있으며, 기기 내 파일 브리지
계층만 한 번 설정하면 그 뒤로는 방해하지 않습니다. 두 원격 경로는 **서로 대체할 수 없습니다**.
MCP는 네트워크 서비스이고, 파일 브리지는 일부 샌드박스 Agent 런타임이 휴대폰의 loopback
포트에 아예 닿지 못하기 때문에 존재합니다.

## 필요한 것

- Android 11(API 30) 이상. 백그라운드 화면은 Android 14 이상에서 가장 안정적입니다.
- **한 번의 ADB 또는 무선 디버깅 승인.** 가상 디스플레이는 `shell` 권한으로 도는 헬퍼
  프로세스가 만들고, 화면 읽기는 접근성 서비스를 지납니다. 권한 없는 방법은 없습니다.
- **직접 준비한 LLM 제공자.** Maeve는 기기에서 실행하지만 계획에는 모델이 필요합니다.
  엔드포인트와 키는 설정에서 지정합니다. 모델 가중치는 동봉하지 않습니다.
- R2를 쓸 때만: 같은 네트워크의 컴퓨터, 또는 USB `adb forward`.

네트워크 설정은 현재 평문 HTTP를 허용합니다. 신뢰할 수 있는 엔드포인트와 직접 관리하는
네트워크를 쓰세요. 확인 게이트를 포괄적인 프라이버시 보장으로 해석하지 마세요.

## 소스에서 빌드

```bash
git clone https://github.com/Daiyimo/Maeve.git
cd Maeve
./gradlew :app:assembleDebug
```

저장소에는 Gradle 모듈 두 개와 내장 Flutter 모듈 하나가 있습니다.

| 경로 | 내용 |
|---|---|
| `:core` | 순수 JVM Kotlin. Agent 루프, 도구, 컨텍스트, 가드, 지식과 스킬. |
| `:app` | Android 호스트. 접근성, 가상 디스플레이, shell 헬퍼, WebView, 샌드박스, 권한, 그리고 아직 쓰이는 Compose 화면. |
| `flutter/` | Compose UI를 대체해 가는 Flutter 셸. `settings.gradle.kts`는 Gradle 모듈로 포함하지 않고, `:app`이 빌드된 debug AAR을 가져다 씁니다. |

AAR이 없으면 Android 호스트는 컴파일되지 않습니다. Flutter 모듈 빌드가 `flutter/` 아래에
`flutter-debug.aar`를 만들고, 생성 디렉터리는 저장소에 들어가지 않습니다.
먼저 Flutter 모듈을 빌드한 뒤 앱을 조립하세요.
툴체인, 캐시, 프록시의 함정은 [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)에 기록되어 있습니다.

릴리스 서명, R8, CI는 [M4](../../SPEC.md#10-里程碑与验收) 작업입니다.

## 현재 상태

저장소는 **M2(상호작용 계층)**이며 UI가 Compose에서 Flutter 셸로 옮겨가는 중입니다.
공개 이력은 Apache-2.0 표시가 있는 단일 베이스라인 스냅숏에서 시작하고, 패키지 이름은
`com.daiyimo.maeve`이며, 준수와 표시 계층은 갖춰져 있습니다. 계보는 [ATTRIBUTION.md](../../ATTRIBUTION.md)를 보세요.

M2 전체 비주얼 시스템, 연결 허브와 bridge v3(M3), 릴리스 강화(M4)는 아직 끝나지 않았습니다.
Flutter 셸은 워크벤치입니다. 답장은 로컬 지연 데모이고, 대화와 메모리는 프로세스 메모리에만 있으며,
Android 호스트는 아직 브리지 핸들러를 등록하지 않습니다.
[FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)에 원형의 한계가 모두 적혀 있습니다.
여기에는 배포된 바이너리가 없습니다.

| 위치 | 내용 |
|---|---|
| [SPEC.md](../../SPEC.md) | 제품 정의, 아키텍처, 마이그레이션, 준수, 마일스톤 |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | Flutter 셸, 모듈 빌드, 툴체인, 한계와 검증 |
| `docs/spec/` | 비주얼 토큰, 햅틱, bridge v3 프로토콜, 마이그레이션 노트 |
| `docs/adr/` | 아키텍처 결정 기록 |
| `docs/dev/` | 아키텍처, 엔지니어링 표준, 리뷰 기록, 개별 노트 |
| [ATTRIBUTION.md](../../ATTRIBUTION.md) | 출처, 바뀐 것, Apache-2.0 §4 진술 |

## 기여

[CONTRIBUTING.md](../../CONTRIBUTING.md)를 보세요. 기여하면 Apache-2.0(inbound = outbound, §5)으로
라이선스하는 데 동의한 것으로 봅니다. 업스트림에는 CLA도 DCO도 없습니다.

## 라이선스와 저작자 표시

Apache-2.0 — [LICENSE](../../LICENSE)를 보세요. `NOTICE`는
`Mika / Copyright 2026 wangzan101`을 그대로 유지합니다. 서드파티와 GPL/LGPL 구성 요소는
[licenses/THIRD_PARTY_NOTICES.md](../../licenses/THIRD_PARTY_NOTICES.md)에 나열되며 APK에도 포함됩니다.

권장 토픽: `android` `ai-agent` `llm-agent` `accessibility-service` `virtual-display`
`kotlin` `jetpack-compose` `flutter` `apache-2.0`.
