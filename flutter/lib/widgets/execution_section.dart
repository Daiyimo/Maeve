import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import 'glass_surface.dart';

/// Aligns the headings and frames without putting a surface behind the headings.
class ExecutionSection extends StatelessWidget {
  const ExecutionSection({
    super.key,
    required this.body,
    this.title,
    this.action,
    this.frameKey,
    this.bodyHeight,
    this.glass = false,
  });

  final String? title;
  final Widget body;
  final Widget? action;
  final Key? frameKey;
  final double? bodyHeight;
  final bool glass;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final headerHeight = math.max(
      40.0,
      MediaQuery.textScalerOf(context).scale(16) + 12,
    );
    final frame = glass
        ? MaeveGlassSurface(
            key: frameKey,
            tier: MaeveGlassTier.card,
            shape: MaeveShapes.card,
            child: body,
          )
        : Container(
            key: frameKey,
            clipBehavior: Clip.antiAlias,
            decoration: BoxDecoration(
              color: c.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.rule),
            ),
            child: body,
          );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (title case final label?)
          SizedBox(
            height: headerHeight,
            child: Padding(
              padding: const EdgeInsetsDirectional.only(start: 6, end: 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: MaeveType.label.copyWith(
                        fontSize: 12,
                        height: 16 / 12,
                        color: c.ink2,
                      ),
                    ),
                  ),
                  ?action,
                ],
              ),
            ),
          ),
        if (bodyHeight == null)
          Expanded(child: frame)
        else
          SizedBox(height: bodyHeight, child: frame),
      ],
    );
  }
}
