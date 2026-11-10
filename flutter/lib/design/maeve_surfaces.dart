import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'maeve_colors.dart';
import 'maeve_theme.dart';

/// 表面层级。四档都是玻璃,但**只有浮在滚动内容之上的两档真的取样模糊**。
///
/// | 层级 | 用在哪 | 模糊 σ | 填充 |
/// |---|---|---|---|
/// | [chrome] | 顶栏、输入栏 | 24 | 浅 0.62 / 深 0.68 |
/// | [sheet] | 底部弹层、抽屉、侧栏 | 30 | 浅 0.74 / 深 0.82 |
/// | [card] | 设置卡、任务卡 | — | 浅 0.58 / 深 0.50 |
/// | [pill] | 状态胶囊、标签 | — | 浅 0.62 / 深 0.60 |
///
/// ## 为什么 card / pill 不取样
///
/// 它们长在**滚动内容里面**:一旦也取样,玻璃看到的就是"一张包含自己的旧画面",
/// 下一帧再录进去,玻璃里套玻璃,越套越深。所以平放的大件只做填充 + 顶边高光 +
/// 内描边 + 一道浅投影,不取样。chrome / sheet 从来不在被录制的内容里(它们是内容的
/// 兄弟节点),取样是安全的 —— 看得见背后真正的滚动内容。
enum MaeveGlassTier { chrome, sheet, card, pill }

/// 一种表面材质的全部参数。
@immutable
class MaeveGlassStyle {
  const MaeveGlassStyle({
    required this.fill,
    required this.fallbackFill,
    required this.hairline,
    required this.rim,
    this.blurSigma = 0,
    this.saturation = 1,
    this.hairlineWidth = .75,
    this.rimFraction = .30,
    this.shadows = const [],
  });

  /// 开模糊时的填充(带透明度)。
  final Color fill;

  /// 关模糊时的填充:必须接近不透明,否则文字会飘在背后的内容上。
  final Color fallbackFill;

  /// 发丝描边:界定玻璃边缘。
  final Color hairline;
  final double hairlineWidth;

  /// 顶边高光:光从上方打在玻璃边上,往里渐隐。
  final Color rim;

  /// 顶边高光最多占元素高度的比例 —— 28dp 的药丸上不该整块都是高光。
  final double rimFraction;

  /// 高斯模糊 σ(逻辑像素)。0 = 不模糊。
  final double blurSigma;

  /// 模糊后的饱和度增益(iOS 式透色)。
  final double saturation;

  /// 只画在形状**外部**的投影(玻璃内部不被阴影弄脏)。
  final List<BoxShadow> shadows;

  bool get blurs => blurSigma > 0;
}

/// 四档表面。
@immutable
class MaeveSurfaces {
  const MaeveSurfaces({
    required this.chrome,
    required this.sheet,
    required this.card,
    required this.pill,
    required this.blurEnabled,
  });

  final MaeveGlassStyle chrome;
  final MaeveGlassStyle sheet;
  final MaeveGlassStyle card;
  final MaeveGlassStyle pill;

  /// 总开关:关掉后所有层级走 fallbackFill,不再创建模糊层。
  final bool blurEnabled;

  MaeveGlassStyle tier(MaeveGlassTier tier) => switch (tier) {
    MaeveGlassTier.chrome => chrome,
    MaeveGlassTier.sheet => sheet,
    MaeveGlassTier.card => card,
    MaeveGlassTier.pill => pill,
  };
}

/// 由颜色和模糊开关出四档表面。数值与 Compose 侧 `buildSurfaces` 逐项一致。
///
/// 唯一一处刻意的偏差:降级填充(`fallbackFill`)按"玻璃色压在画布上再取不透明"
/// 算,而不是把半透明填充的 alpha 直接改成 0.97。后者在深色下会把 6% 白的表面
/// 变成 97% 白 —— 一关模糊,顶栏就白成一块塑料。降级只是"不透明",不是"变白"。
MaeveSurfaces buildSurfaces(MaeveColors colors, {bool blurEnabled = true}) {
  Color opaque(Color fill) => Color.alphaBlend(fill, colors.bg);
  return MaeveSurfaces(
    blurEnabled: blurEnabled,
    chrome: MaeveGlassStyle(
      fill: colors.glass,
      fallbackFill: opaque(colors.glass),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      blurSigma: blurEnabled ? 24 : 0,
      saturation: 1.40,
      rimFraction: .42,
      shadows: [
        BoxShadow(
          color: colors.floatShadow.withValues(alpha: .13),
          blurRadius: 14,
          offset: const Offset(0, 7),
        ),
      ],
    ),
    sheet: MaeveGlassStyle(
      fill: colors.glassSheet,
      fallbackFill: opaque(colors.glassSheet),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      blurSigma: blurEnabled ? 30 : 0,
      saturation: 1.55,
      rimFraction: .16,
      shadows: [
        BoxShadow(
          color: colors.floatShadow.withValues(alpha: .19),
          blurRadius: 22,
          offset: const Offset(0, 11),
        ),
      ],
    ),
    card: MaeveGlassStyle(
      fill: colors.glassCard,
      fallbackFill: opaque(colors.glassCard),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      rimFraction: .34,
      shadows: [
        BoxShadow(
          color: colors.floatShadow.withValues(alpha: .10),
          blurRadius: 4,
          offset: const Offset(0, 2),
        ),
      ],
    ),
    pill: MaeveGlassStyle(
      fill: colors.glassPill,
      fallbackFill: opaque(colors.glassPill),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      rimFraction: .50,
    ),
  );
}

/// 全屏页面背景:**中性竖渐变 + 三团极光**。
///
/// 毛玻璃需要有东西可透:纯色背景模糊之后还是那个纯色,只是糊了一点,看不出是玻璃。
/// 三团低饱和色斑(一颗跟主题色、一颗邻近色、一颗固定冷色)给玻璃提供折射原料,
/// 玻璃件因此能读出位置感 —— 左上角的卡和右下角的卡透出来的颜色不一样。
///
/// 极光压得很淡(α 0.11–0.30):太艳会把玻璃染成花纸。
/// **同一主题内所有页共用同一张背景**,切页面时背景不动。
class MaeveBackdrop extends StatelessWidget {
  const MaeveBackdrop({super.key, this.glow = true});

  final bool glow;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [colors.bgTop, colors.bg, colors.bgBottom],
          stops: const [0, .58, 1],
        ),
      ),
      child: glow
          ? CustomPaint(
              painter: _AuroraPainter(
                primary: colors.glowPrimary,
                secondary: colors.glowSecondary,
                tertiary: colors.glowTertiary,
              ),
              size: Size.infinite,
            )
          : const SizedBox.expand(),
    );
  }
}

class _AuroraPainter extends CustomPainter {
  const _AuroraPainter({
    required this.primary,
    required this.secondary,
    required this.tertiary,
  });

  final Color primary;
  final Color secondary;
  final Color tertiary;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final unit = math.min(size.width, size.height);
    void glow(Color color, Offset center, double radius) {
      canvas.drawRect(
        Offset.zero & size,
        Paint()
          ..shader = RadialGradient(colors: [color, color.withValues(alpha: 0)])
              .createShader(Rect.fromCircle(center: center, radius: radius)),
      );
    }

    glow(primary, Offset(size.width * .14, size.height * .06), unit * .92);
    glow(secondary, Offset(size.width * .92, size.height * .26), unit * .78);
    glow(tertiary, Offset(size.width * .44, size.height * 1.02), unit * .86);
  }

  @override
  bool shouldRepaint(_AuroraPainter old) =>
      old.primary != primary ||
      old.secondary != secondary ||
      old.tertiary != tertiary;
}
