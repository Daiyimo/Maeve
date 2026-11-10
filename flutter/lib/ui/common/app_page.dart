import 'package:flutter/material.dart';

import 'haptics.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_surfaces.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';

import '../../widgets/glass_surface.dart';

/// 二级页骨架:**整条玻璃顶栏 + 可滚动内容**。
///
/// 三条约定:
/// - 顶栏是一条 56 高的玻璃(chrome 档),内容从它**底下**滚过去,被它采样模糊;
///   没有额外的渐隐层 —— 填充 + 发丝线已经保证标题在滚动时仍然可读。
/// - 标题相对**整条顶栏**居中:左右各占一个固定宽的格子,中间那块永远在正中。
/// - 返回是 40 的圆钮;进页用 220ms 的减速推入(不是 Material 的上升淡入)。
class AppPage extends StatefulWidget {
  const AppPage({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.eyebrow,
    this.actions = const [],
    this.padded = true,
    this.scrollController,
    this.showBack = true,
  });

  /// 列表页:居中阅读列 + 顶部留出顶栏。
  factory AppPage.list({
    Key? key,
    required String title,
    required List<Widget> children,
    String? subtitle,
    String? eyebrow,
    List<Widget> actions = const [],
    EdgeInsets? padding,
    ScrollController? controller,
    bool showBack = true,
  }) => AppPage(
    key: key,
    title: title,
    subtitle: subtitle,
    eyebrow: eyebrow,
    actions: actions,
    scrollController: controller,
    showBack: showBack,
    child: ListView(
      controller: controller,
      padding:
          padding ??
          const EdgeInsets.fromLTRB(
            MaeveSpace.s16,
            MaeveSize.topBar + MaeveSpace.s8,
            MaeveSpace.s16,
            MaeveSpace.s32,
          ),
      children: children,
    ),
  );

  final String title;
  final String? subtitle;

  /// 标题上方那行等宽小字(带一个强调色小点)。
  final String? eyebrow;

  final Widget child;
  final List<Widget> actions;
  final bool padded;
  final ScrollController? scrollController;
  final bool showBack;

  /// 打开一个二级页。
  static Future<T?> open<T>(BuildContext context, Widget page) =>
      Navigator.of(context).push<T>(maevePageRoute<T>((_) => page));

  @override
  State<AppPage> createState() => _AppPageState();
}

class _AppPageState extends State<AppPage> {
  /// 内容滚到顶栏下面时给顶栏换一层更实的填充:玻璃仍然取样,但字更稳。
  bool _scrolled = false;

  bool _onScroll(ScrollNotification notification) {
    final next = notification.metrics.pixels > 2;
    if (next != _scrolled && mounted) setState(() => _scrolled = next);
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final safeTop = MediaQuery.viewPaddingOf(context).top;

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          const Positioned.fill(child: MaeveBackdrop()),
          Positioned.fill(
            child: Padding(
              padding: EdgeInsets.only(top: safeTop),
              child: NotificationListener<ScrollNotification>(
                onNotification: _onScroll,
                child: widget.padded
                    ? Center(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(
                            maxWidth: MaeveSpace.readingWidth,
                          ),
                          child: widget.child,
                        ),
                      )
                    : widget.child,
              ),
            ),
          ),
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: TopBar(
              title: widget.title,
              subtitle: widget.subtitle,
              eyebrow: widget.eyebrow,
              actions: widget.actions,
              showBack: widget.showBack,
              elevated: _scrolled,
            ),
          ),
        ],
      ),
    );
  }
}

/// 顶栏:整条玻璃 + 左圆钮 + 居中标题列(眉标 / 标题 / 副标题) + 右动作区。
class TopBar extends StatelessWidget {
  const TopBar({
    super.key,
    required this.title,
    this.subtitle,
    this.eyebrow,
    this.actions = const [],
    this.showBack = true,
    this.onBack,
    this.leading,
    this.brand = false,
    this.elevated = false,
  });

  final String title;
  final String? subtitle;
  final String? eyebrow;
  final List<Widget> actions;
  final bool showBack;
  final VoidCallback? onBack;

  /// 覆盖左侧按钮(聊天页放抽屉键)。
  final Widget? leading;

  /// 标题左边带品牌标(对话页),顶栏高出一档给三层文字。
  final bool brand;

  /// 内容已滚到下面:填充更实一点。
  final bool elevated;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final safeTop = MediaQuery.viewPaddingOf(context).top;
    final canPop = Navigator.of(context).canPop();
    final height = brand ? MaeveSize.topBarBrand : MaeveSize.topBar;
    // 左右两格等宽,标题才真的居中;右侧有几个动作就按几个 48 算。
    final sideWidth = 48.0 * (actions.isEmpty ? 1 : actions.length);

    return BackdropGroup(
      child: Container(
        padding: EdgeInsets.only(top: safeTop),
        color: Colors.transparent,
        child: MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          grouped: true,
          shape: const RoundedRectangleBorder(),
          blur: true,
          // 滚动后不用换材质,玻璃本身会采样;这里只在滚动时把填充压得更实一档。
          tint: elevated ? colors.ink : null,
          tintAlpha: elevated ? .02 : null,
          child: SizedBox(
            height: height,
            child: Row(
              children: [
                SizedBox(
                  width: 48,
                  child:
                      leading ??
                      (showBack && canPop
                          ? _BarButton(
                              icon: Icons.arrow_back_ios_new_rounded,
                              label: '返回',
                              onTap:
                                  onBack ??
                                  () => Navigator.of(context).maybePop(),
                            )
                          : const SizedBox.shrink()),
                ),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (eyebrow != null) ...[
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              width: 4,
                              height: 4,
                              decoration: BoxDecoration(
                                color: colors.accent,
                                shape: BoxShape.circle,
                              ),
                            ),
                            const SizedBox(width: 5),
                            Text(
                              eyebrow!,
                              style: MaeveType.eyebrowFor(eyebrow!)
                                  .copyWith(color: colors.ink3),
                            ),
                          ],
                        ),
                        const SizedBox(height: 2),
                      ],
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (brand) ...[
                            const _BrandDot(),
                            const SizedBox(width: 8),
                          ],
                          Flexible(
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              textAlign: TextAlign.center,
                              style: MaeveType.title.copyWith(
                                color: colors.ink,
                              ),
                            ),
                          ),
                        ],
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: 1),
                        Text(
                          subtitle!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                          style: MaeveType.hint.copyWith(color: colors.ink3),
                        ),
                      ],
                    ],
                  ),
                ),
                SizedBox(
                  width: sideWidth,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: actions,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// 顶栏 / 行内的小圆钮(40,可点域 44)。
class _BarButton extends StatelessWidget {
  const _BarButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Tooltip(
      message: label,
      child: Semantics(
        button: true,
        label: label,
        child: InkWell(
          customBorder: const CircleBorder(),
          splashFactory: NoSplash.splashFactory,
          highlightColor: Colors.transparent,
          onTap: () {
            MaeveHaptics.play(MaeveHaptic.tick);
            onTap();
          },
          child: SizedBox.square(
            dimension: 48,
            child: Center(child: Icon(icon, size: 17, color: colors.ink)),
          ),
        ),
      ),
    );
  }
}

/// 标题左边那个小环:Maeve 的品牌标缩到 18 的尺寸。
class _BrandDot extends StatelessWidget {
  const _BrandDot();

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: 18,
    child: Center(
      child: Icon(
        Icons.circle_outlined,
        size: 14,
        color: MaeveTheme.of(context).colors.accent,
      ),
    ),
  );
}

/// iOS 式水平推入:进场 220ms 减速,退场 220ms 加速离开。
Route<T> maevePageRoute<T>(WidgetBuilder builder) => PageRouteBuilder<T>(
  transitionDuration: MaeveMotion.page,
  reverseTransitionDuration: MaeveMotion.page,
  pageBuilder: (context, animation, secondaryAnimation) => builder(context),
  transitionsBuilder: (context, animation, secondaryAnimation, child) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    final curved = CurvedAnimation(
      parent: animation,
      curve: MaeveMotion.decelerate,
      reverseCurve: Curves.easeInCubic,
    );
    return SlideTransition(
      position: Tween<Offset>(
        begin: const Offset(.06, 0),
        end: Offset.zero,
      ).animate(curved),
      child: FadeTransition(opacity: curved, child: child),
    );
  },
);
