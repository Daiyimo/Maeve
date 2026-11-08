import 'package:flutter/material.dart';

import '../design/maeve_tokens.dart';

enum MaeveGlassTier { chrome, sheet, card, pill }

class MaeveGlassSurface extends StatelessWidget {
  const MaeveGlassSurface({
    super.key,
    required this.child,
    this.tier = MaeveGlassTier.card,
    this.padding,
    this.tint,
    this.onTap,
    this.borderRadius,
  });

  final Widget child;
  final MaeveGlassTier tier;
  final EdgeInsetsGeometry? padding;
  final Color? tint;
  final VoidCallback? onTap;
  final BorderRadius? borderRadius;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final radius = borderRadius ?? _radiusForTier();
    final base = tint ?? theme.colorScheme.surfaceContainerHighest;
    final fill = dark
        ? Color.alphaBlend(
            base.withValues(alpha: .34),
            theme.colorScheme.surface,
          )
        : Color.alphaBlend(
            base.withValues(alpha: .58),
            theme.scaffoldBackgroundColor,
          );
    final line = theme.dividerColor;

    Widget surface = RepaintBoundary(
      child: Container(
        padding: padding,
        decoration: BoxDecoration(
          color: fill,
          borderRadius: radius,
          border: Border.all(color: line),
          boxShadow: tier == MaeveGlassTier.sheet
              ? [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: dark ? .12 : .055),
                    blurRadius: 18,
                    offset: const Offset(0, 7),
                  ),
                ]
              : null,
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Colors.white.withValues(alpha: dark ? .055 : .62),
              fill,
              Colors.black.withValues(alpha: dark ? .035 : .025),
            ],
            stops: const [0, .28, 1],
          ),
        ),
        child: child,
      ),
    );

    if (onTap == null) return ClipRRect(borderRadius: radius, child: surface);

    return Semantics(
      button: true,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: radius,
          onTap: onTap,
          child: ClipRRect(borderRadius: radius, child: surface),
        ),
      ),
    );
  }

  BorderRadius _radiusForTier() => switch (tier) {
    MaeveGlassTier.chrome => BorderRadius.circular(MaeveTokens.radiusMedium),
    MaeveGlassTier.sheet => BorderRadius.circular(MaeveTokens.radiusLarge),
    MaeveGlassTier.card => BorderRadius.circular(MaeveTokens.radiusMedium),
    MaeveGlassTier.pill => BorderRadius.circular(MaeveTokens.radiusPill),
  };
}

class MaeveGlassButton extends StatelessWidget {
  const MaeveGlassButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.prominent = false,
    this.compact = false,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool prominent;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final color = prominent ? Theme.of(context).colorScheme.primary : null;
    final foreground = prominent
        ? Theme.of(context).colorScheme.onPrimary
        : Theme.of(context).colorScheme.onSurface;
    final side = prominent
        ? BorderSide.none
        : BorderSide(color: Theme.of(context).dividerColor);

    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        label: tooltip,
        child: Material(
          color: color ?? Theme.of(context).colorScheme.surfaceContainerHighest,
          shape: CircleBorder(side: side),
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onPressed,
            child: SizedBox.square(
              dimension: compact ? 38 : 44,
              child: Icon(icon, size: compact ? 18 : 20, color: foreground),
            ),
          ),
        ),
      ),
    );
  }
}
