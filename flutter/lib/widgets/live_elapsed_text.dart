import 'dart:async';

import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/duration_format.dart';

/// 等待模型响应时的本地实时用时；宿主快照到达后用新基线校准。
class LiveElapsedText extends StatefulWidget {
  const LiveElapsedText({super.key, required this.elapsedMs, this.style});

  final int elapsedMs;
  final TextStyle? style;

  @override
  State<LiveElapsedText> createState() => _LiveElapsedTextState();
}

class _LiveElapsedTextState extends State<LiveElapsedText> {
  Timer? _timer;
  late int _elapsedMs;

  @override
  void initState() {
    super.initState();
    _elapsedMs = widget.elapsedMs;
    _timer = Timer.periodic(const Duration(milliseconds: 200), (_) {
      if (mounted) setState(() => _elapsedMs += 200);
    });
  }

  @override
  void didUpdateWidget(covariant LiveElapsedText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.elapsedMs != widget.elapsedMs) _elapsedMs = widget.elapsedMs;
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Text(
    durationLabel(_elapsedMs),
    style: (widget.style ?? MaeveType.mono).copyWith(
      color: MaeveTheme.of(context).colors.ink3,
      fontWeight: FontWeight.w600,
    ),
  );
}
