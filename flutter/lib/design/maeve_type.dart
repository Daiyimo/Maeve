import 'package:flutter/material.dart';

import 'maeve_colors.dart';

/// 字号与字重。
///
/// ## 两套字,三种声部
///
/// 界面只有两种字体:**系统无衬线**说人话,**等宽**说机器话。这不是装饰,是分层:
///
/// 1. **等宽微标签**([eyebrow])——`LOOP 03`、`CRADLE · RUNNING`。宽字距是它的
///    全部意义:等宽 + 宽字距 = 读数,不是正文。**用一次是签名,用五次是噪音**,
///    所以顶栏只留一行,分组标题一律用无衬线的 [label]。
/// 2. **等宽读数**([mono]/[numeral])——耗时、步数、token、版本号、时间。数字一律
///    tabular,跳动时不左右抖。
/// 3. **无衬线正文**——其余一切。
///
/// ## 字重只用 400 / 500 / 600
///
/// AOSP 只带 Noto Sans CJK Regular,w500 会被映射回 Regular、w600+ 走合成加粗。
/// 所以层级主要靠**字号 + 颜色 + 字距**,字重只在真正需要压住的地方用 600。
/// 大号展示字用 500(不用 Bold 合成,中文加粗合成后笔画会糊)。
///
/// ## 行高与 baseline
///
/// 全部样式带 `leadingDistribution: even` —— 中文字形没有西文的降部,默认的
/// `proportional` 分布会让中文行看起来**偏上**,多行段落的上浓下空。改成 even 之后
/// 中文在行盒里居中,同样的行高读起来更松。正文行高从 1.48 抬到 1.55,
/// 她的回答([script])抬到 1.68 —— 那是全 App 唯一需要被"读"的文本。
///
/// ## 不打包中文字体
///
/// 中文跟随系统(MiSans / OPPO Sans / Noto Sans CJK 都行);等宽也走系统等宽,
/// 和 Compose 侧同一套字形 —— 两套 UI 在同一个屏幕上必须长得一样。
abstract final class MaeveType {
  /// 等宽出口:业务代码不写 fontFamily 字面量,统一走这里。
  static const monoFamily = 'monospace';
  static const monoFallback = <String>[
    'monospace',
    'Noto Sans Mono CJK SC',
    'Noto Sans CJK SC',
  ];

  static const _tabular = <FontFeature>[
    FontFeature.tabularFigures(),
    // 关掉编程连字:`->`、`!=` 在界面里要按原样读。
    FontFeature.disable('liga'),
    FontFeature.disable('calt'),
  ];

  /// 中文行盒居中(见类注释)。每个样式都带上。
  static const _even = TextLeadingDistribution.even;

  // ---------- 无衬线 ----------

  /// 展示字:欢迎页、空状态问候。负字距是"精密感"的来源。
  ///
  /// 33 而不是 30:上一版的标题和卡片标题只差一档,读起来像"加粗的正文"。
  /// 标题要敢大,层级才立得住。
  static const display = TextStyle(
    fontSize: 38,
    height: 44 / 38,
    fontWeight: FontWeight.w600,
    letterSpacing: -1.1,
    leadingDistribution: _even,
  );

  /// 页面级大标题。
  static const largeTitle = TextStyle(
    fontSize: 30,
    height: 38 / 30,
    fontWeight: FontWeight.w500,
    letterSpacing: -.7,
    leadingDistribution: _even,
  );

  /// 页面标题:顶栏。
  static const title = TextStyle(
    fontSize: 16.5,
    height: 22 / 16.5,
    fontWeight: FontWeight.w500,
    letterSpacing: -.2,
    leadingDistribution: _even,
  );

  /// 正文、行标题。
  static const body = TextStyle(
    fontSize: 15.5,
    height: 24 / 15.5,
    fontWeight: FontWeight.w400,
    letterSpacing: -.05,
    leadingDistribution: _even,
  );

  /// 需要强调的正文(卡片标题)。
  static const bodyStrong = TextStyle(
    fontSize: 15.5,
    height: 24 / 15.5,
    fontWeight: FontWeight.w500,
    leadingDistribution: _even,
  );

  /// 说明文字。
  static const caption = TextStyle(
    fontSize: 13.5,
    height: 20 / 13.5,
    fontWeight: FontWeight.w400,
    leadingDistribution: _even,
  );

  /// 分组小标题。
  static const label = TextStyle(
    fontSize: 12.5,
    height: 18 / 12.5,
    fontWeight: FontWeight.w500,
    letterSpacing: .1,
    leadingDistribution: _even,
  );

  /// 提示、页脚。
  static const hint = TextStyle(
    fontSize: 12,
    height: 17 / 12,
    fontWeight: FontWeight.w400,
    leadingDistribution: _even,
  );

  /// 行尾的值。
  static const value = TextStyle(
    fontSize: 15,
    height: 21 / 15,
    fontWeight: FontWeight.w400,
    leadingDistribution: _even,
  );

  /// 她的回答正文:全 App 唯一需要被"读"的文本,行高给到 1.68。
  static const script = TextStyle(
    fontSize: 16,
    height: 27 / 16,
    fontWeight: FontWeight.w400,
    letterSpacing: -.05,
    leadingDistribution: _even,
  );

  // ---------- 等宽 ----------

  /// 微标签:小号、宽字距 —— 它是一行读数,不是一句说明。
  static const eyebrow = TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 10.5,
    height: 14 / 10.5,
    fontWeight: FontWeight.w500,
    letterSpacing: .7,
    leadingDistribution: _even,
  );

  /// 更小一档的微标签:卡片角标、行内分类。
  static const eyebrowSmall = TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 9.5,
    height: 13 / 9.5,
    fontWeight: FontWeight.w500,
    letterSpacing: .6,
    leadingDistribution: _even,
  );

  /// 等宽读数:耗时、步数、token、版本号。
  static const mono = TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 12,
    height: 16 / 12,
    fontWeight: FontWeight.w400,
    letterSpacing: .2,
    fontFeatures: _tabular,
    leadingDistribution: _even,
  );

  /// 小一档读数。
  static const monoSmall = TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 10.5,
    height: 14 / 10.5,
    fontWeight: FontWeight.w400,
    fontFeatures: _tabular,
    leadingDistribution: _even,
  );

  /// 大一号读数:用量页、关于页的主数字。
  static const numeral = TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 22,
    height: 26 / 22,
    fontWeight: FontWeight.w500,
    letterSpacing: -.3,
    fontFeatures: _tabular,
    leadingDistribution: _even,
  );

  /// 台词轨上的轮次号。
  static const turn = TextStyle(
    fontFamily: monoFamily,
    fontFamilyFallback: monoFallback,
    fontSize: 10,
    height: 12 / 10,
    fontWeight: FontWeight.w500,
    fontFeatures: _tabular,
    leadingDistribution: _even,
  );

  /// 微标签的中文档:含中文时自动升到 11.5(汉字 10.5 会把笔锋糊掉),
  /// 同时收掉字距 —— 汉字不需要西文那种宽字距。
  static TextStyle eyebrowFor(String text) => _hasCjk(text)
      ? eyebrow.copyWith(fontSize: 11.5, letterSpacing: .2)
      : eyebrow;

  /// 判断一段文字里有没有中日韩字符。
  static bool _hasCjk(String text) => text.runes.any((rune) => rune > 0x2E80);

  /// 值里没有中文时自动走等宽 —— "读数感"自动扩散的关键:一处判断,全站生效。
  static TextStyle valueFor(String text) =>
      _hasCjk(text) ? value : mono.copyWith(fontSize: 13, height: 18 / 13);
}

/// 间距。以 4 的倍数为主,6 / 10 是给图标与行内间隙的补充。
abstract final class MaeveSpace {
  static const s2 = 2.0;
  static const s4 = 4.0;
  static const s6 = 6.0;
  static const s8 = 8.0;
  static const s10 = 10.0;
  static const s12 = 12.0;
  static const s16 = 16.0;
  static const s20 = 20.0;
  static const s24 = 24.0;
  static const s32 = 32.0;
  static const s40 = 40.0;

  /// 页面左右留白。
  static const page = 18.0;

  /// 阅读列最大宽度(平板 / 横屏居中)。
  static const readingWidth = 720.0;
}

/// 断点:更宽时列表常驻,更窄时抽屉拉开。
abstract final class MaeveBreakpoints {
  static const wide = 840.0;
}

/// 各类元素的尺寸。
abstract final class MaeveSize {
  static const topBar = 56.0;

  /// 带品牌标的顶栏(三层:标志 / 标题 / 状态)。
  static const topBarBrand = 70.0;

  static const iconButton = 40.0;
  static const rowMin = 52.0;
  static const button = 52.0;
  static const composer = 104.0;
  static const icon = 22.0;

  /// 列表行左边的图标块,里面的图形是 [tileIcon]。
  static const tile = 30.0;
  static const tileIcon = 18.0;
  static const smallIcon = 16.0;
  static const iconStroke = 1.7;

  /// 台词轨的宽度(竖线 + 轮次号那一栏)。
  static const actRail = 26.0;

  /// 会话抽屉宽度。
  static const drawer = 286.0;

  /// 最小可点区域。
  static const touch = 44.0;
}

/// 把 [MaeveColors] 装成 Flutter 的 [TextTheme]。
///
/// 颜色在这里一次性绑好;字号 / 字重 / 行高全部来自 [MaeveType] 的常量,
/// 这样"字阶"只有一个来源,不会在页面里散落一堆 `copyWith(fontSize:)`。
TextTheme maeveTextTheme(MaeveColors colors) {
  TextStyle paint(TextStyle style, Color color) => style.copyWith(color: color);
  return TextTheme(
    displayLarge: paint(MaeveType.display, colors.ink),
    displayMedium: paint(MaeveType.largeTitle, colors.ink),
    displaySmall: paint(MaeveType.largeTitle, colors.ink),
    headlineMedium: paint(MaeveType.largeTitle, colors.ink),
    headlineSmall: paint(MaeveType.display, colors.ink),
    titleLarge: paint(MaeveType.title, colors.ink),
    titleMedium: paint(MaeveType.title, colors.ink),
    titleSmall: paint(MaeveType.bodyStrong, colors.ink),
    bodyLarge: paint(MaeveType.body, colors.ink),
    bodyMedium: paint(MaeveType.body, colors.ink),
    bodySmall: paint(MaeveType.caption, colors.ink3),
    labelLarge: paint(
      MaeveType.bodyStrong.copyWith(fontSize: 14.5),
      colors.ink,
    ),
    labelMedium: paint(MaeveType.label.copyWith(fontSize: 12.5), colors.ink3),
    labelSmall: paint(MaeveType.eyebrow, colors.ink3),
  );
}
