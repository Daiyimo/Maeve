import 'dart:async';

import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/duration_format.dart';

/// 实时显示任务累计用时，并用宿主快照向前校准。
class LiveElapsedText extends StatefulWidget {
  const LiveElapsedText({super.key, required this.elapsedMs, this.style});

  final int elapsedMs;
  final TextStyle? style;

  @override
  State<LiveElapsedText> createState() => _LiveElapsedTextState();
}

class _LiveElapsedTextState extends State<LiveElapsedText> {
  static const _interval = Duration(milliseconds: 200);
  late final Timer _timer;
  late int _baselineMs;
  int get _elapsedMs => _baselineMs + _timer.tick * _interval.inMilliseconds;

  @override
  void initState() {
    super.initState();
    _baselineMs = widget.elapsedMs;
    _timer = Timer.periodic(_interval, (_) => setState(() {}));
  }

  @override
  void didUpdateWidget(covariant LiveElapsedText oldWidget) {
    super.didUpdateWidget(oldWidget);
    final correction = widget.elapsedMs - _elapsedMs;
    if (correction > 0) _baselineMs += correction;
  }

  @override
  void dispose() {
    _timer.cancel();
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
