#!/usr/bin/env bash
# Maeve 真机冒烟：装包 → 走一遍主要入口 → 检查 native crash。
#
# 为什么需要这个：编译通过、单测通过，都不代表应用没崩。
# 这个项目在 v0.4～v0.8 期间带着一个 RenderThread 栈溢出（hwui DirtyStack
# 只增不减 → computeTransformImpl 递归 → SIGSEGV），编译和单测全绿，
# 但点「设置」「关于」整页必崩。渲染 / 模糊 / 图层相关的改动必须跑这个。
#
# 用法：tooling/smoke.sh [设备序列号]
set -uo pipefail

ADB="${ADB:-$HOME/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.daiyimo.maeve"
ACTIVITY="$PKG/.ui.MainActivity"
SERIAL="${1:-}"

adb_() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

[ -f "$APK" ] || { echo "找不到 $APK，先跑 :app:assembleDebug"; exit 1; }

echo "── 安装"
adb_ install -r "$APK" | tail -1
adb_ logcat -c

crashes() { adb_ logcat -d 2>/dev/null | grep -cE "Fatal signal|stack overflow"; }

# 每个入口：冷启动 → 开侧栏 → 点 → 等 → 记录首屏文字
check() {
  local name="$1" x="$2" y="$3"
  adb_ shell am force-stop "$PKG"; sleep 1
  adb_ shell am start -n "$ACTIVITY" >/dev/null 2>&1; sleep 5
  adb_ shell input tap 91 264; sleep 2
  adb_ shell input tap "$x" "$y"; sleep 4
  adb_ shell uiautomator dump /sdcard/_smoke.xml >/dev/null 2>&1
  local txt
  txt=$(adb_ shell cat /sdcard/_smoke.xml 2>/dev/null | tr '<' '\n' | grep -oE 'text="[^"]+"' | head -1)
  printf '  %-10s %s\n' "$name" "${txt:-（无文字）}"
}

echo "── 入口"
check "主界面" 0 0     # 只开侧栏，不点入口
check "定时任务" 250 410
check "记忆与技能" 280 553
check "浏览器" 230 696
check "设置" 217 2462
check "关于" 550 2462

echo "── 长按消息菜单 / 语音（手工补）"
echo "    侧栏宽度：$(adb_ shell wm size | sed 's/.*: //')"

N=$(crashes)
echo
if [ "$N" -eq 0 ]; then
  echo "✅ 冒烟通过：0 次 native crash"
else
  echo "❌ 发现 $N 次 native crash："
  adb_ logcat -d 2>/dev/null | grep -E "Fatal signal" | tail -5
  echo
  echo "最常见的根因：给内容层或整棵容器套 graphicsLayer { renderEffect = BlurEffect(...) }。"
  echo "hwui 的 DirtyStack 容量是 512，只增不减，满了就在 computeTransformImpl 里栈溢出。"
  echo "改法：模糊只做在离屏层上，别挂在包含其它 layer 的容器上。"
  exit 1
fi
