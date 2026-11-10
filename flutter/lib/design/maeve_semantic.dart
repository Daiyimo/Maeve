import 'package:flutter/foundation.dart';

import 'maeve_colors.dart';
import 'maeve_oklch.dart';

import 'package:flutter/painting.dart';

/// 运行步骤的视觉类别。色相本身就是语义:一张「读文件」的行在任何配色下都必须长得一样。
enum ToolTone { terminal, read, edit, search, web, agent, plan, neutral }

/// 语义色:与强调色**无关**的固定色。
///
/// 状态色一律低饱和 —— 鲜绿鲜红一出现,界面立刻变成"运维面板"。
/// 全部由 OKLCH 现推(固定色相 + 明度带),文字对其底色 ≥ 4.5、容器上 on-色 ≥ 4.5、图形 ≥ 3。
@immutable
class MaeveSemantic {
  const MaeveSemantic({
    required this.ok,
    required this.okContainer,
    required this.warn,
    required this.warnSolid,
    required this.warnContainer,
    required this.bad,
    required this.badContainer,
    required this.info,
    required this.infoContainer,
    required this.toolContainers,
    required this.onToolContainers,
    required this.identity,
    required this.onIdentity,
  });

  final Color ok;
  final Color okContainer;
  final Color warn;
  final Color warnSolid;
  final Color warnContainer;
  final Color bad;
  final Color badContainer;
  final Color info;
  final Color infoContainer;

  final List<Color> toolContainers;
  final List<Color> onToolContainers;
  final List<Color> identity;
  final List<Color> onIdentity;

  /// 工具类别的 (容器, on-容器)。
  (Color, Color) tool(ToolTone tone) =>
      (toolContainers[tone.index], onToolContainers[tone.index]);

  /// 由稳定字符串(会话 id)映射到身份色下标。
  /// 不用 hashCode:各平台都不保证跨进程稳定,重启后颜色会整体重排。固定 FNV-1a。
  int identityIndex(String key, {int slots = 8}) {
    var hash = 0x811c9dc5;
    for (final unit in key.codeUnits) {
      hash ^= unit;
      hash = (hash * 0x01000193) & 0xFFFFFFFF;
    }
    return hash % slots;
  }

  static const _hOk = 150.0;
  static const _hWarn = 70.0;
  static const _hBad = 25.0;
  static const _hInfo = 245.0;
  static const _toolHues = [
    260.0,
    220.0,
    60.0,
    290.0,
    160.0,
    330.0,
    190.0,
    0.0,
  ];
  static const _identityHues = [
    220.0,
    270.0,
    320.0,
    20.0,
    60.0,
    140.0,
    180.0,
    250.0,
  ];

  static final MaeveSemantic light = _build(
    textL: .45,
    textC: .11,
    containerL: .92,
    containerC: .05,
    onContainerL: .32,
    onContainerC: .09,
    solidL: .62,
    solidC: .13,
  );

  static final MaeveSemantic dark = _build(
    textL: .78,
    textC: .10,
    containerL: .30,
    containerC: .055,
    onContainerL: .88,
    onContainerC: .07,
    solidL: .70,
    solidC: .12,
  );

  static MaeveSemantic _build({
    required double textL,
    required double textC,
    required double containerL,
    required double containerC,
    required double onContainerL,
    required double onContainerC,
    required double solidL,
    required double solidC,
  }) {
    Color at(double l, double c, double hue) =>
        MaeveOklch.color(l: l, c: c, hue: hue);
    return MaeveSemantic(
      ok: at(textL, textC, _hOk),
      okContainer: at(containerL, containerC, _hOk),
      warn: at(textL, textC, _hWarn),
      warnSolid: at(solidL, solidC, _hWarn),
      warnContainer: at(containerL, containerC, _hWarn),
      bad: at(textL, textC, _hBad),
      badContainer: at(containerL, containerC, _hBad),
      info: at(textL, textC, _hInfo),
      infoContainer: at(containerL, containerC, _hInfo),
      toolContainers: [
        for (final hue in _toolHues)
          hue == 0
              ? at(containerL + .01, .012, 260)
              : at(containerL, containerC + .01, hue),
      ],
      onToolContainers: [
        for (final hue in _toolHues)
          hue == 0
              ? at(onContainerL, .014, 260)
              : at(onContainerL, onContainerC + .02, hue),
      ],
      identity: [
        for (final hue in _identityHues) at(containerL, containerC + .008, hue),
      ],
      onIdentity: [
        for (final hue in _identityHues)
          at(onContainerL, onContainerC + .02, hue),
      ],
    );
  }

  static MaeveSemantic of(Brightness brightness) =>
      brightness == Brightness.dark ? dark : light;
}

/// 语义色的可达性守卫:文字色对背景 ≥4.5、图形 ≥3。
/// 只在单测里用,运行时不做二次修正(色值本身已经过守卫)。
MaeveSemantic guardedSemantic(MaeveSemantic semantic, MaeveColors colors) =>
    MaeveSemantic(
      ok: MaeveOklch.guard(semantic.ok, colors.bg, 4.5),
      okContainer: semantic.okContainer,
      warn: MaeveOklch.guard(semantic.warn, colors.bg, 4.5),
      warnSolid: semantic.warnSolid,
      warnContainer: semantic.warnContainer,
      bad: MaeveOklch.guard(semantic.bad, colors.bg, 4.5),
      badContainer: semantic.badContainer,
      info: MaeveOklch.guard(semantic.info, colors.bg, 4.5),
      infoContainer: semantic.infoContainer,
      toolContainers: semantic.toolContainers,
      onToolContainers: semantic.onToolContainers,
      identity: semantic.identity,
      onIdentity: semantic.onIdentity,
    );
