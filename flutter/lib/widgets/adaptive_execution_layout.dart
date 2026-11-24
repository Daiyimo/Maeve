import 'package:flutter/material.dart';

/// 执行画面与记录/结算的左右双栏容器；窄屏只压缩内容，不改为上下。
class AdaptiveExecutionLayout extends StatelessWidget {
  const AdaptiveExecutionLayout({
    super.key,
    required this.left,
    required this.right,
    required this.wideHeight,
    this.compactHeight = 340,
    this.compactLeft,
    this.compactRight,
    this.breakpoint = 600,
  });

  final Widget left;
  final Widget right;
  final Widget? compactLeft;
  final Widget? compactRight;
  final double wideHeight;
  final double compactHeight;
  final double breakpoint;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final compact = constraints.maxWidth < breakpoint;
      return SizedBox(
        height: compact ? compactHeight : wideHeight,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(flex: 48, child: compact ? compactLeft ?? left : left),
            SizedBox(width: compact ? 8 : 12),
            Expanded(flex: 52, child: compact ? compactRight ?? right : right),
          ],
        ),
      );
    },
  );
}
