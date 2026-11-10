import 'package:flutter/foundation.dart';

import 'maeve_accent.dart';
import 'maeve_oklch.dart';

import 'package:flutter/painting.dart';

/// 界面用到的全部颜色。页面一律从这里取,不写死色值。
///
/// ## 这套配色照 Linear 的规矩来
///
/// 四条硬规矩(与 Compose 侧 `MaeveColors.kt` 同一组决策):
///
/// 1. **一个彩色。** 全站只有 [accent] 一种有彩度的颜色,只用在品牌标、焦点环、
///    每屏一个主按钮。状态(成功/警告/失败)**不靠色相**,靠图形、字重和表面层级。
/// 2. **表面阶梯代替阴影。** [surfaceLowest] → [bg] → [surface] → [surfaceHigh] →
///    [surfaceHighest] 五级,相邻两级只差 4–6 个明度点,几乎看不出来;
///    层级由**发丝线**([hair]/[rule]/[edge])承担。全站零投影(浮件例外)。
/// 3. **画布是中性灰,不是暖白。** 大面积铺开不显脏、不显旧;色相固定偏冷 250,
///    彩度千分之二,和屏幕、系统 UI 都不打架。
/// 4. **文字用四级灰,不用彩色说话。** ink / ink2 / ink3 / ink4 全是从画布底色
///    按明度阶梯推出来的同一个灰。
///
/// 中性色的色相**不随强调色转**:配色一换整张纸的色相跟着跑,界面就失去稳定性。
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
    required this.floatShadow,
    required this.thumb,
    required this.scrim,
    required this.glowPrimary,
    required this.glowSecondary,
    required this.glowTertiary,
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

  final Color floatShadow;
  final Color thumb;
  final Color scrim;

  /// 三团极光:主色一团、邻近色一团、固定的冷色一团。
  /// 它们不是装饰,是给玻璃折射用的原料。
  final Color glowPrimary;
  final Color glowSecondary;
  final Color glowTertiary;

  /// 极淡的网格线(叶子面板用)。
  final Color grid;

  /// 中性画布的色相:偏冷一点点(蓝侧),彩度极低,固定不转。
  static const neutralHue = 250.0;

  static Color _n(double l, double c) =>
      MaeveOklch.color(l: l, c: c, hue: neutralHue);

  static MaeveColors light(MaeveAccent accent) {
    final canvas = _n(.973, .0016);
    final ink = _n(.208, .006);
    final ink2 = MaeveOklch.guard(_n(.452, .005), canvas, 4.5);
    final ink3 = MaeveOklch.guard(_n(.585, .004), canvas, 3);
    return MaeveColors(
      isDark: false,
      bg: canvas,
      bgTop: _n(.978, .0014),
      bgBottom: _n(.963, .0018),
      // 半透明白:几十处直接拿它当底画矩形,给不透明值就永远成不了玻璃。
      // 白度 0.58:太低会和背景只差一两个色阶(太透等于没有层),太高退回白纸。
      surface: const Color(0xFFFFFFFF).withValues(alpha: .58),
      surfaceLowest: const Color(0xFFFFFFFF).withValues(alpha: .30),
      surfaceHigh: const Color(0xFFFFFFFF).withValues(alpha: .68),
      surfaceHighest: const Color(0xFFFFFFFF).withValues(alpha: .74),
      fill: ink.withValues(alpha: .045),
      ink: ink,
      ink2: ink2,
      ink3: ink3,
      ink4: MaeveOklch.guard(_n(.700, .003), canvas, 2.2),
      hair: ink.withValues(alpha: .07),
      rule: ink.withValues(alpha: .135),
      edge: ink.withValues(alpha: .08),
      accent: accent.lightSeed,
      accentPressed: accent.lightPressed,
      accentContainer: accent.lightContainer.withValues(alpha: .60),
      onAccentContainer: MaeveOklch.guard(
        accent.lightOnContainer,
        accent.lightContainer,
        4.5,
      ),
      accentSoft: accent.lightContainer.withValues(alpha: .55),
      onAccent: const Color(0xFFFFFFFF),
      ok: MaeveOklch.color(l: .478, c: .088, hue: 152),
      okSoft: MaeveOklch.color(
        l: .945,
        c: .022,
        hue: 152,
      ).withValues(alpha: .55),
      warn: MaeveOklch.color(l: .500, c: .105, hue: 60),
      warnSolid: MaeveOklch.color(l: .580, c: .115, hue: 60),
      warnSoft: MaeveOklch.color(
        l: .950,
        c: .028,
        hue: 60,
      ).withValues(alpha: .55),
      bad: MaeveOklch.color(l: .492, c: .140, hue: 26),
      badSoft: MaeveOklch.color(
        l: .948,
        c: .026,
        hue: 26,
      ).withValues(alpha: .55),
      neutralSoft: ink.withValues(alpha: .048),
      greySoft: ink.withValues(alpha: .075),
      glass: const Color(0xFFFFFFFF).withValues(alpha: .62),
      glassSheet: const Color(0xFFFFFFFF).withValues(alpha: .74),
      glassCard: const Color(0xFFFFFFFF).withValues(alpha: .58),
      glassPill: const Color(0xFFFFFFFF).withValues(alpha: .62),
      // 边是玻璃唯一"硬"的轮廓:0.085 时整块玻璃没有边界,读作一片发白的水渍。
      glassEdge: ink.withValues(alpha: .16),
      glassRim: const Color(0xFFFFFFFF).withValues(alpha: .95),
      floatShadow: const Color(0xFF000000),
      thumb: const Color(0xFFFFFFFF),
      scrim: const Color(0xFF000000).withValues(alpha: .30),
      glowPrimary: accent.lightGlow.withValues(alpha: .26),
      glowSecondary: accent.hazeLight.withValues(alpha: .18),
      glowTertiary: const Color(0xFF3E6C9E).withValues(alpha: .11),
      grid: ink.withValues(alpha: .055),
    );
  }

  static MaeveColors dark(MaeveAccent accent) {
    final canvas = _n(.145, .0022);
    final ink = _n(.962, .0016);
    final ink2 = MaeveOklch.guard(_n(.740, .003), canvas, 4.5);
    final ink3 = MaeveOklch.guard(_n(.580, .004), canvas, 3);
    return MaeveColors(
      isDark: true,
      bg: canvas,
      bgTop: _n(.157, .0024),
      bgBottom: _n(.132, .0020),
      surface: const Color(0xFFFFFFFF).withValues(alpha: .062),
      surfaceLowest: const Color(0xFFFFFFFF).withValues(alpha: .028),
      surfaceHigh: const Color(0xFFFFFFFF).withValues(alpha: .088),
      surfaceHighest: const Color(0xFFFFFFFF).withValues(alpha: .115),
      fill: ink.withValues(alpha: .058),
      ink: ink,
      ink2: ink2,
      ink3: ink3,
      ink4: MaeveOklch.guard(_n(.470, .004), canvas, 2.2),
      hair: ink.withValues(alpha: .085),
      rule: ink.withValues(alpha: .155),
      edge: ink.withValues(alpha: .095),
      accent: accent.darkSeed,
      accentPressed: accent.darkPressed,
      accentContainer: accent.darkContainer,
      onAccentContainer: MaeveOklch.guard(
        accent.darkOnContainer,
        accent.darkContainer,
        4.5,
      ),
      accentSoft: accent.darkContainer.withValues(alpha: .60),
      onAccent: accent.darkOnSeed,
      ok: MaeveOklch.color(l: .760, c: .100, hue: 152),
      okSoft: MaeveOklch.color(
        l: .268,
        c: .038,
        hue: 152,
      ).withValues(alpha: .60),
      warn: MaeveOklch.color(l: .780, c: .110, hue: 62),
      warnSolid: MaeveOklch.color(l: .740, c: .115, hue: 62),
      warnSoft: MaeveOklch.color(
        l: .300,
        c: .045,
        hue: 62,
      ).withValues(alpha: .60),
      bad: MaeveOklch.color(l: .700, c: .150, hue: 26),
      badSoft: MaeveOklch.color(
        l: .300,
        c: .055,
        hue: 26,
      ).withValues(alpha: .60),
      neutralSoft: ink.withValues(alpha: .055),
      greySoft: ink.withValues(alpha: .125),
      // 深色玻璃靠「白度」立起来:填充是「黑 + 一点点白」,不是「白 + 一点点黑」。
      glass: _n(.215, .0026).withValues(alpha: .68),
      glassSheet: _n(.245, .0028).withValues(alpha: .82),
      glassCard: _n(.195, .0026).withValues(alpha: .50),
      glassPill: _n(.215, .0026).withValues(alpha: .60),
      glassEdge: ink.withValues(alpha: .11),
      // 深色玻璃的边光要比浅色弱:强了会变成一圈白描边。
      glassRim: const Color(0xFFFFFFFF).withValues(alpha: .20),
      floatShadow: const Color(0xFF000000),
      thumb: const Color(0xFFFFFFFF),
      scrim: const Color(0xFF000000).withValues(alpha: .50),
      glowPrimary: accent.darkGlow.withValues(alpha: .30),
      glowSecondary: accent.hazeDark.withValues(alpha: .22),
      glowTertiary: const Color(0xFF2B5A94).withValues(alpha: .20),
      grid: ink.withValues(alpha: .065),
    );
  }
}
