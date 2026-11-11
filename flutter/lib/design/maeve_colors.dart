import 'package:flutter/foundation.dart';

import 'maeve_accent.dart';
import 'maeve_oklch.dart';

import 'package:flutter/painting.dart';

/// A complete atmosphere: tinted canvas, guarded inks, accent and optical layers.
/// Scrolling cards use static highlights; anchored chrome samples the backdrop.
@immutable
class MaeveColors {
  const MaeveColors({
    required this.isDark,
    required this.bg,
    required this.bgTop,
    required this.bgBottom,
    required this.surface,
    required this.surfaceLowest,
    required this.surfaceHigh,
    required this.surfaceHighest,
    required this.fill,
    required this.ink,
    required this.ink2,
    required this.ink3,
    required this.ink4,
    required this.hair,
    required this.rule,
    required this.edge,
    required this.accent,
    required this.accentPressed,
    required this.accentContainer,
    required this.onAccentContainer,
    required this.accentSoft,
    required this.onAccent,
    required this.ok,
    required this.okSoft,
    required this.warn,
    required this.warnSolid,
    required this.warnSoft,
    required this.bad,
    required this.badSoft,
    required this.neutralSoft,
    required this.greySoft,
    required this.glass,
    required this.glassSheet,
    required this.glassCard,
    required this.glassPill,
    required this.glassEdge,
    required this.glassRim,
    required this.sheen,
    required this.floatShadow,
    required this.thumb,
    required this.scrim,
    required this.glowPrimary,
    required this.glowSecondary,
    required this.glowTertiary,
    required this.bloom,
    required this.vignette,
    required this.grid,
  });

  final bool isDark;

  /// 页面背景(画布)与它的竖向渐变两端。
  final Color bg;
  final Color bgTop;
  final Color bgBottom;

  /// 五级表面阶梯(低 → 高)。浅色下是半透明白,深色下是半透明白叠加:
  /// 它们是玻璃的填充,不是不透明纸。
  final Color surfaceLowest;
  final Color surface;
  final Color surfaceHigh;
  final Color surfaceHighest;

  /// 浅填充:次按钮、分段控件底、代码块底。
  final Color fill;

  /// 文字四级:正文 / 次要 / 辅助 / 极弱。
  final Color ink;
  final Color ink2;
  final Color ink3;
  final Color ink4;

  /// 行分隔线 / 更重的一道(台词轨、选中态) / 卡片描边。
  final Color hair;
  final Color rule;
  final Color edge;

  /// 唯一的彩色。
  final Color accent;
  final Color accentPressed;
  final Color accentContainer;
  final Color onAccentContainer;
  final Color accentSoft;
  final Color onAccent;

  /// 状态:低饱和,靠图形与字重,不靠色相。
  final Color ok;
  final Color okSoft;
  final Color warn;
  final Color warnSolid;
  final Color warnSoft;
  final Color bad;
  final Color badSoft;

  /// 图标块的底 / 灰色胶囊、关着的开关。
  final Color neutralSoft;
  final Color greySoft;

  /// 四档玻璃填充与它们共用的描边 / 顶边高光。
  final Color glass;
  final Color glassSheet;
  final Color glassCard;
  final Color glassPill;
  final Color glassEdge;
  final Color glassRim;

  /// 玻璃内部自上而下的一道提亮(顶部起,到底部收成透明)。
  /// 不是白描边:它让玻璃看起来被上方的光"照到"了。
  final Color sheen;

  final Color floatShadow;
  final Color thumb;
  final Color scrim;

  /// 三团极光:主色一团、邻近色一团、固定的冷色一团。
  /// 它们不是装饰,是给玻璃折射用的原料。
  final Color glowPrimary;
  final Color glowSecondary;
  final Color glowTertiary;

  /// 背景里唯一的光源:一小团更亮、更集中的光。极光负责铺,它负责"亮"。
  final Color bloom;

  /// 四角压暗。有它才有纵深:没有暗角,极光看起来像一块脏印子。
  final Color vignette;

  /// 极淡的网格线(叶子面板用)。
  final Color grid;

  static const neutralHue = 300.0;

  static (double, double) _atmosphere(MaeveAccent accent) =>
      switch (accent.id) {
        'reverie' => (290, .010),
        'dolores' => (345, .018),
        'maze' => (145, .014),
        'pearl' => (220, .015),
        'sublime' => (275, .016),
        'graphite' => (80, .006),
        _ => (accent.hue, .010),
      };

  /// 玻璃的高光一律是纯白:暖色高光会让玻璃发黄,读作"旧"。
  static const _light = Color(0xFFFFFFFF);

  static MaeveColors light(MaeveAccent accent) {
    final (hue, chroma) = _atmosphere(accent);
    Color neutral(double l, double c) => MaeveOklch.color(l: l, c: c, hue: hue);
    final canvas = neutral(.973, chroma * .6);
    final ink = neutral(.205, .0130);
    final ink2 = MaeveOklch.guard(neutral(.450, .0115), canvas, 4.5);
    final ink3 = MaeveOklch.guard(neutral(.490, .0105), canvas, 4.5);
    return MaeveColors(
      isDark: false,
      bg: canvas,
      bgTop: neutral(.980, chroma * .4),
      bgBottom: neutral(.965, chroma * .7),
      // 半透明白:几十处直接拿它当底画矩形,给不透明值就永远成不了玻璃。
      surface: _light.withValues(alpha: .72),
      surfaceLowest: _light.withValues(alpha: .46),
      surfaceHigh: _light.withValues(alpha: .82),
      surfaceHighest: _light.withValues(alpha: .92),
      fill: ink.withValues(alpha: .050),
      ink: ink,
      ink2: ink2,
      ink3: ink3,
      ink4: MaeveOklch.guard(neutral(.700, .0090), canvas, 2.2),
      hair: ink.withValues(alpha: .085),
      rule: ink.withValues(alpha: .150),
      edge: ink.withValues(alpha: .110),
      accent: accent.lightSeed,
      accentPressed: accent.lightPressed,
      accentContainer: accent.lightContainer.withValues(alpha: .72),
      onAccentContainer: MaeveOklch.guard(
        accent.lightOnContainer,
        accent.lightContainer,
        4.5,
      ),
      accentSoft: accent.lightContainer.withValues(alpha: .66),
      onAccent: _light,
      ok: MaeveOklch.color(l: .455, c: .105, hue: 150),
      okSoft: MaeveOklch.color(
        l: .930,
        c: .030,
        hue: 150,
      ).withValues(alpha: .62),
      warn: MaeveOklch.color(l: .470, c: .105, hue: 68),
      warnSolid: MaeveOklch.color(l: .620, c: .120, hue: 68),
      warnSoft: MaeveOklch.color(
        l: .938,
        c: .038,
        hue: 68,
      ).withValues(alpha: .62),
      bad: MaeveOklch.color(l: .470, c: .145, hue: 22),
      badSoft: MaeveOklch.color(
        l: .936,
        c: .032,
        hue: 22,
      ).withValues(alpha: .62),
      neutralSoft: ink.withValues(alpha: .052),
      greySoft: ink.withValues(alpha: .090),
      glass: _light.withValues(alpha: .58),
      glassSheet: _light.withValues(alpha: .78),
      glassCard: _light.withValues(alpha: .52),
      glassPill: _light.withValues(alpha: .78),
      // 边是玻璃唯一"硬"的轮廓:太淡整块玻璃没有边界,读作一片发白的水渍。
      glassEdge: ink.withValues(alpha: .085),
      glassRim: _light.withValues(alpha: .45),
      sheen: _light.withValues(alpha: .16),
      floatShadow: const Color(0xFF3A2E24),
      thumb: _light,
      scrim: const Color(0xFF2A211A).withValues(alpha: .32),
      // 浅色下极光必须非常淡:奶色纸上只要有一点带色光的雾,整屏立刻读作"粉紫"
      // 而不是"暖纸"。层次交给白卡片和那道暖阴影,极光只负责让纸不要死平。
      glowPrimary: accent.lightGlow.withValues(alpha: .09),
      glowSecondary: accent.hazeLight.withValues(alpha: .04),
      glowTertiary: const Color(0xFF7E9BC0).withValues(alpha: .012),
      bloom: _light.withValues(alpha: .08),
      vignette: neutral(.760, .0140).withValues(alpha: .025),
      grid: ink.withValues(alpha: .055),
    );
  }

  static MaeveColors dark(MaeveAccent accent) {
    final (hue, chroma) = _atmosphere(accent);
    Color neutral(double l, double c) => MaeveOklch.color(l: l, c: c, hue: hue);
    final canvas = neutral(.225, chroma);
    final ink = neutral(.966, .0050);
    final ink2 = MaeveOklch.guard(neutral(.780, .0072), canvas, 4.5);
    final ink3 = MaeveOklch.guard(neutral(.720, .0086), canvas, 4.5);
    return MaeveColors(
      isDark: true,
      bg: canvas,
      bgTop: neutral(.246, chroma),
      bgBottom: neutral(.215, chroma * .8),
      surface: _light.withValues(alpha: .075),
      surfaceLowest: _light.withValues(alpha: .040),
      surfaceHigh: _light.withValues(alpha: .100),
      surfaceHighest: _light.withValues(alpha: .130),
      fill: ink.withValues(alpha: .065),
      ink: ink,
      ink2: ink2,
      ink3: ink3,
      ink4: MaeveOklch.guard(neutral(.505, .0098), canvas, 2.2),
      hair: ink.withValues(alpha: .080),
      rule: ink.withValues(alpha: .150),
      edge: ink.withValues(alpha: .120),
      accent: accent.darkSeed,
      accentPressed: accent.darkPressed,
      accentContainer: accent.darkContainer,
      onAccentContainer: MaeveOklch.guard(
        accent.darkOnContainer,
        accent.darkContainer,
        4.5,
      ),
      accentSoft: accent.darkContainer.withValues(alpha: .72),
      onAccent: accent.darkOnSeed,
      // 状态绿降一档彩度:高饱和的荧光绿在这张暖画布上像一条告警灯带。
      // 就绪不是告警,它只需要比墨色多一点点"活着"的意思。
      ok: MaeveOklch.color(l: .780, c: .075, hue: 150),
      okSoft: MaeveOklch.color(
        l: .300,
        c: .045,
        hue: 150,
      ).withValues(alpha: .72),
      warn: MaeveOklch.color(l: .800, c: .112, hue: 78),
      warnSolid: MaeveOklch.color(l: .740, c: .120, hue: 78),
      warnSoft: MaeveOklch.color(
        l: .320,
        c: .052,
        hue: 78,
      ).withValues(alpha: .72),
      bad: MaeveOklch.color(l: .700, c: .145, hue: 22),
      badSoft: MaeveOklch.color(
        l: .320,
        c: .062,
        hue: 22,
      ).withValues(alpha: .72),
      neutralSoft: ink.withValues(alpha: .060),
      greySoft: ink.withValues(alpha: .135),
      // Tinted neutral fills preserve separation without white bloom.
      glass: neutral(.410, chroma).withValues(alpha: .48),
      glassSheet: neutral(.315, chroma).withValues(alpha: .88),
      glassCard: neutral(.460, chroma).withValues(alpha: .30),
      glassPill: neutral(.350, chroma).withValues(alpha: .62),
      glassEdge: ink.withValues(alpha: .085),
      // 深色玻璃的边光要比浅色弱:强了会变成一圈白描边。
      glassRim: _light.withValues(alpha: .24),
      sheen: _light.withValues(alpha: .035),
      floatShadow: const Color(0xFF000000),
      thumb: _light,
      scrim: const Color(0xFF000000).withValues(alpha: .52),
      glowPrimary: accent.darkGlow.withValues(alpha: .16),
      glowSecondary: accent.hazeDark.withValues(alpha: .065),
      glowTertiary: const Color(0xFF2F5E8C).withValues(alpha: .020),
      // 光源比极光更亮、彩度更低:极光是"有颜色的空气",光源是"灯"。
      // 彩度只留强调色的三分之一,否则整块背景变成一块彩色果冻。
      bloom: MaeveOklch.color(
        l: .760,
        c: accent.chroma * .34,
        hue: accent.hue,
      ).withValues(alpha: .12),
      vignette: const Color(0xFF000000).withValues(alpha: .08),
      grid: ink.withValues(alpha: .065),
    );
  }
}
