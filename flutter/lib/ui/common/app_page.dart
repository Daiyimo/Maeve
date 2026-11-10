import 'package:flutter/material.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_surfaces.dart';
import '../../design/maeve_tokens.dart';
import 'pressable.dart';

/// 二级页骨架:标题栏 56 + 内容 + 顶渐隐 + 返回圆钮 44。
///
/// 三条约定:
/// - 标题栏浮在内容上,内容滚到栏下才出现渐隐(C11),静止页没有一条色带;
/// - 返回是 chrome 圆钮 44(和顶栏的 ≡ 同一个尺寸),不是文字箭头;
/// - 进页用 iOS 式水平推入(300ms + 长尾减速),不是 Android 的上升淡入。
class AppPage extends StatefulWidget {
  const AppPage({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.actions = const [],
    this.padded = true,
    this.scrollController,
    this.showBack = true,
  });

  /// 列表页:居中阅读列 + 顶部留出标题栏。
  factory AppPage.list({
    Key? key,
    required String title,
    required List<Widget> children,
    String? subtitle,
    List<Widget> actions = const [],
    EdgeInsets? padding,
    ScrollController? controller,
    bool showBack = true,
  }) => AppPage(
    key: key,
    title: title,
    subtitle: subtitle,
    actions: actions,
    scrollController: controller,
    showBack: showBack,
    child: ListView(
      controller: controller,
      padding:
          padding ??
          const EdgeInsets.fromLTRB(
            MaeveSpace.gutter,
            MaeveLayout.pageBarHeight + MaeveSpace.md,
            MaeveSpace.gutter,
            MaeveSpace.xxxl,
          ),
      children: children,
    ),
  );

  final String title;
  final String? subtitle;
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
  bool _scrolled = false;

  /// 用滚动通知判断,不依赖具体是哪个 controller —— 列表可能是调用方
  /// 建的,也可能根本没有 controller(用默认的)。
  bool _onScroll(ScrollNotification notification) {
    final next = notification.metrics.pixels > 4;
    if (next != _scrolled && mounted) {
      setState(() => _scrolled = next);
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final surfaces = MaeveSurfaces.of(context);
    final motion = AppMotion.of(context);
    final left = widget.padded ? MaeveSpace.gutter : 0.0;
    // 状态栏高度要算进来:标题栏贴顶会被系统图标压住。
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
          // 顶渐隐:只有内容滚到标题栏下面才出现。
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: IgnorePointer(
              child: AnimatedOpacity(
                opacity: _scrolled ? 1 : 0,
                duration: motion.quick,
                curve: Curves.easeOut,
                child: Container(
                  height: safeTop + MaeveLayout.pageBarHeight + 10,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        surfaces.backdropTop,
                        surfaces.backdropTop.withValues(alpha: .82),
                        surfaces.backdropTop.withValues(alpha: 0),
                      ],
                      stops: const [0, .62, 1],
                    ),
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            top: safeTop,
            left: left,
            right: left,
            child: _PageBar(
              title: widget.title,
              subtitle: widget.subtitle,
              actions: widget.actions,
              showBack: widget.showBack,
            ),
          ),
        ],
      ),
    );
  }
}

class _PageBar extends StatelessWidget {
  const _PageBar({
    required this.title,
    required this.subtitle,
    required this.actions,
    required this.showBack,
  });

  final String title;
  final String? subtitle;
  final List<Widget> actions;
  final bool showBack;

  @override
  Widget build(BuildContext context) {
    final canPop = Navigator.of(context).canPop();
    return SizedBox(
      height: MaeveLayout.pageBarHeight,
      child: Row(
        children: [
          SizedBox(
            width: MaeveLayout.touch,
            child: showBack && canPop
                ? PageBackButton(
                    onPressed: () => Navigator.of(context).maybePop(),
                  )
                : null,
          ),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if (subtitle != null)
                  Text(
                    subtitle!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
              ],
            ),
          ),
          SizedBox(
            width: MaeveLayout.touch,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: actions,
            ),
          ),
        ],
      ),
    );
  }
}

/// 返回圆钮:chrome 玻璃、44×44,与顶栏圆钮同尺寸同材质。
class PageBackButton extends StatelessWidget {
  const PageBackButton({super.key, required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Center(
      child: Pressable(
        onTap: onPressed,
        borderRadius: BorderRadius.circular(MaeveRadius.pill),
        semanticLabel: '返回',
        child: SizedBox.square(
          dimension: MaeveLayout.touch,
          child: Center(
            child: Icon(
              Icons.arrow_back_ios_new_rounded,
              size: 17,
              color: scheme.onSurface,
            ),
          ),
        ),
      ),
    );
  }
}

/// iOS 式水平推入:进场 300ms 长尾减速,退场 300ms 加速离开。
Route<T> maevePageRoute<T>(WidgetBuilder builder) => PageRouteBuilder<T>(
  transitionDuration: MaeveMotion.push,
  reverseTransitionDuration: MaeveMotion.push,
  pageBuilder: (context, animation, secondaryAnimation) => builder(context),
  transitionsBuilder: (context, animation, secondaryAnimation, child) {
    if (MediaQuery.disableAnimationsOf(context)) return child;
    final curved = CurvedAnimation(
      parent: animation,
      curve: MaeveMotion.glide,
      reverseCurve: MaeveMotion.exit,
    );
    return SlideTransition(
      position: Tween<Offset>(
        begin: const Offset(.14, 0),
        end: Offset.zero,
      ).animate(curved),
      child: FadeTransition(opacity: curved, child: child),
    );
  },
);
