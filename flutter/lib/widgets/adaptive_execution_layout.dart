import 'package:flutter/material.dart';

import '../design/maeve_type.dart';

/// 执行工作区始终并排展示画面和记录，窄屏为记录多留一点宽度。
class AdaptiveExecutionLayout extends StatelessWidget {
  const AdaptiveExecutionLayout({
    super.key,
    required this.left,
    required this.right,
    required this.wideHeight,
    this.compactHeight = 302,
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
      return Padding(
        padding: const EdgeInsetsDirectional.only(end: MaeveSpace.s12),
        child: SizedBox(
          height: compact ? compactHeight : wideHeight,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                flex: compact ? 48 : 57,
                child: compact ? compactLeft ?? left : left,
              ),
              SizedBox(width: compact ? MaeveSpace.s8 : MaeveSpace.s12),
              Expanded(
                flex: compact ? 52 : 43,
                child: compact ? compactRight ?? right : right,
              ),
            ],
          ),
        ),
      );
    },
  );
}
