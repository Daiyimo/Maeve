import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/execution.dart';
import '../ui/common/haptics.dart';
import 'execution_section.dart';
import 'glass_surface.dart';

/// 固定高度的实时记录窗口；记录从上到下追加，最新记录停在底部。
class LiveExecutionFeed extends StatefulWidget {
  const LiveExecutionFeed({
    super.key,
    required this.steps,
    this.height,
    this.compact = false,
  });
  final List<ExecutionStep> steps;
  final double? height;
  final bool compact;

  @override
  State<LiveExecutionFeed> createState() => _LiveExecutionFeedState();
}

class _LiveExecutionFeedState extends State<LiveExecutionFeed> {
  final _scroll = ScrollController();
  bool _following = true;
  double _extent = 92;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      final follow =
          _scroll.position.pixels >= _scroll.position.maxScrollExtent - 24;
      if (follow != _following && mounted) setState(() => _following = follow);
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scroll.hasClients) {
        _scroll.jumpTo(_scroll.position.maxScrollExtent);
      }
    });
  }

  @override
  void didUpdateWidget(covariant LiveExecutionFeed oldWidget) {
    super.didUpdateWidget(oldWidget);
    final offset = _scroll.hasClients ? _scroll.offset : 0.0;
    final follow = _following;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scroll.hasClients) return;
      _scroll.jumpTo(
        follow
            ? _scroll.position.maxScrollExtent
            : offset.clamp(0, _scroll.position.maxScrollExtent),
      );
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final scale = MediaQuery.textScalerOf(context).scale(14) / 14;
    final scaled = (widget.compact ? 76 : 92) * scale;
    _extent = scaled.clamp(widget.compact ? 72 : 92, 180);
    final titleStyle = widget.compact
        ? MaeveType.label.copyWith(fontSize: 11.5, height: 15 / 11.5)
        : MaeveType.caption;
    final metaStyle = widget.compact
        ? MaeveType.hint.copyWith(fontSize: 10.5, height: 14 / 10.5)
        : MaeveType.hint;
    final followLabel = _following ? '正在跟随最新' : '回到最新';
    final list = widget.steps.isEmpty
        ? Center(
            child: Text(
              '等待第一条执行记录',
              style: MaeveType.caption.copyWith(color: c.ink3),
            ),
          )
        : ListView.builder(
            key: ValueKey('live-execution-list-${widget.steps.length}'),
            controller: _scroll,
            itemExtent: _extent,
            itemCount: widget.steps.length,
            padding: EdgeInsets.symmetric(horizontal: widget.compact ? 6 : 10),
            itemBuilder: (context, index) {
              final step = widget.steps[index];
              final detail = step.detail.isNotEmpty
                  ? step.detail
                  : step.actions.map((a) => a.label).join(' · ');
              return Container(
                key: ValueKey('live-step-${step.step}'),
                margin: EdgeInsets.only(bottom: widget.compact ? 6 : 8),
                padding: EdgeInsets.symmetric(
                  horizontal: widget.compact ? 7 : 10,
                  vertical: widget.compact ? 6 : 8,
                ),
                decoration: BoxDecoration(
                  color: index == widget.steps.length - 1
                      ? c.accentSoft
                      : c.fill,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      '${step.step.toString().padLeft(2, '0')}  ${step.title}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: titleStyle.copyWith(
                        color: step.failed ? c.bad : c.ink,
                        fontWeight: index == widget.steps.length - 1
                            ? FontWeight.w600
                            : FontWeight.w500,
                      ),
                    ),
                    if (step.reason.isNotEmpty && step.reason != step.title)
                      Text(
                        step.reason,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: metaStyle.copyWith(color: c.ink2),
                      ),
                    if (detail.isNotEmpty)
                      Text(
                        detail,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: metaStyle.copyWith(
                          color: step.failed ? c.bad : c.ink3,
                        ),
                      ),
                  ],
                ),
              );
            },
          );
    final jumpToLatest = _following
        ? null
        : () => _scroll.jumpTo(_scroll.position.maxScrollExtent);
    return Padding(
      padding: widget.compact || widget.height != null
          ? EdgeInsets.zero
          : const EdgeInsets.only(top: 8, bottom: 4),
      child: ExecutionSection(
        title: '实时执行记录',
        frameKey: const ValueKey('live-execution-feed'),
        bodyHeight: widget.height == null
            ? (_extent * 1.8).clamp(160, 230)
            : null,
        body: list,
        action: widget.compact
            ? SizedBox.square(
                dimension: 36,
                child: MaeveGlassButton(
                  icon: Icons.keyboard_double_arrow_down_rounded,
                  tooltip: followLabel,
                  visualDiameter: 28,
                  haptic: MaeveHaptic.tick,
                  onPressed: jumpToLatest,
                ),
              )
            : MaeveGlassTextButton(
                label: followLabel,
                compact: true,
                enabled: !_following,
                haptic: MaeveHaptic.tick,
                onPressed: jumpToLatest,
              ),
      ),
    );
  }
}
