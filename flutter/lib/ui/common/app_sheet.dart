import 'package:flutter/material.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';

import '../../widgets/glass_surface.dart';

/// 底部弹层:sheet 玻璃 + 顶两角 28 + 抓手 + 标题栏。
///
/// 升起 380ms、退出 260ms;停稳后模糊才取样,所以拖动中不闪。
Future<T?> showMaeveSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  bool dismissible = true,
  bool isScrollControlled = true,
}) {
  final colors = MaeveTheme.of(context).colors;
  final motion = AppMotion.of(context);
  final controller =
      BottomSheet.createAnimationController(Navigator.of(context))
        ..duration = motion.sheetIn
        ..reverseDuration = motion.standard;
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: isScrollControlled,
    backgroundColor: Colors.transparent,
    barrierColor: colors.scrim,
    elevation: 0,
    isDismissible: dismissible,
    enableDrag: dismissible,
    transitionAnimationController: controller,
    builder: (context) => builder(context),
  );
}

/// 弹层本体。放进 [showMaeveSheet] 的 builder 里。
class AppSheet extends StatelessWidget {
  const AppSheet({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.fromLTRB(
      MaeveSpace.s16,
      MaeveSpace.s8,
      MaeveSpace.s16,
      MaeveSpace.s16,
    ),
  });

  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final animation = ModalRoute.of(context)?.animation;
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * .92,
        ),
        child: AnimatedBuilder(
          animation: animation ?? const AlwaysStoppedAnimation(1),
          builder: (context, _) {
            // 弹层一起床就容易闪:scheduled 采样要等它停稳。
            final settled = animation == null
                ? 1.0
                : Curves.easeOut.transform(
                    ((animation.value - .55) / .45).clamp(0, 1),
                  );
            return MaeveGlassSurface(
              tier: MaeveGlassTier.sheet,
              shape: MaeveShapes.sheet,
              settled: settled,
              child: Padding(padding: padding, child: child),
            );
          },
        ),
      ),
    );
  }
}

/// 弹层标题栏:抓手 + 标题(+副标题) + 右侧动作。
class SheetTitleBar extends StatelessWidget {
  const SheetTitleBar({
    super.key,
    required this.title,
    this.subtitle,
    this.actions = const [],
    this.showGrabber = true,
  });

  final String title;
  final String? subtitle;
  final List<Widget> actions;
  final bool showGrabber;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Column(
      children: [
        if (showGrabber)
          Padding(
            padding: const EdgeInsets.only(top: 2, bottom: MaeveSpace.s12),
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: colors.ink3.withValues(alpha: .28),
                borderRadius: BorderRadius.circular(MaeveRadius.pill),
              ),
            ),
          ),
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: MaeveType.title.copyWith(color: colors.ink),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      style: MaeveType.hint.copyWith(color: colors.ink3),
                    ),
                  ],
                ],
              ),
            ),
            for (final action in actions) ...[
              const SizedBox(width: MaeveSpace.s4),
              action,
            ],
          ],
        ),
      ],
    );
  }
}
