import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/execution.dart';
import '../ui/common/haptics.dart';
import 'execution_section.dart';
import 'glass_surface.dart';
import 'task_preview.dart';

/// 固定高度的实时记录窗口；记录从上到下追加，最新记录停在底部。
class LiveExecutionFeed extends StatefulWidget {
  const LiveExecutionFeed({
    super.key,
    required this.steps,
    this.height,
    this.compact = false,
    this.showHeader = true,
    this.recordings = const {},
    this.selected,
    this.onSelect,
  });
  final List<ExecutionStep> steps;
  final double? height;
  final bool compact;
  final bool showHeader;
  final Map<int, RecordedPreview> recordings;
  final RecordedPreview? selected;
  final ValueChanged<RecordedPreview>? onSelect;

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
              final last = index == widget.steps.length - 1;
              final lastInStep =
                  last || widget.steps[index + 1].step != step.step;
              final recording = lastInStep
                  ? widget.recordings[step.step]
                  : null;
              final selected =
                  recording != null && widget.selected == recording;
              final active = widget.selected == null && last;
              final detail = step.detail.isNotEmpty
                  ? step.detail
                  : step.actions.map((a) => a.label).join(' · ');
              final label = Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      step.title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: titleStyle.copyWith(
                        color: step.failed
                            ? c.bad
                            : active || selected
                            ? c.accent
                            : c.ink,
                        fontWeight: FontWeight.w600,
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
              return Row(
                key: ValueKey('live-step-${step.step}'),
                children: [
                  SizedBox(
                    width: 23,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        if (index > 0 || !last)
                          Align(
                            alignment: last
                                ? Alignment.topCenter
                                : Alignment.bottomCenter,
                            child: FractionallySizedBox(
                              heightFactor: index == 0 || last ? .5 : 1,
                              child: VerticalDivider(width: 1, color: c.rule),
                            ),
                          ),
                        Center(
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: step.failed
                                  ? c.badSoft
                                  : last
                                  ? c.accentSoft
                                  : c.okSoft,
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(3),
                              child: Icon(
                                step.failed
                                    ? Icons.close_rounded
                                    : last
                                    ? Icons.circle
                                    : Icons.check_rounded,
                                size: 10,
                                color: step.failed
                                    ? c.bad
                                    : last
                                    ? c.accent
                                    : c.ok,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 3),
                      child: Material(
                        color: selected || active
                            ? c.accentSoft
                            : Colors.transparent,
                        borderRadius: BorderRadius.circular(10),
                        child: Semantics(
                          selected: selected,
                          child: InkWell(
                            borderRadius: BorderRadius.circular(10),
                            onTap: recording == null || widget.onSelect == null
                                ? null
                                : () => widget.onSelect!(recording),
                            child: Row(
                              children: [
                                Expanded(child: label),
                                if (recording != null)
                                  Padding(
                                    padding: const EdgeInsetsDirectional.only(
                                      end: 8,
                                    ),
                                    child: Icon(
                                      Icons.image_outlined,
                                      size: 16,
                                      color: c.ink3,
                                      semanticLabel: '回看画面',
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
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
        title: widget.showHeader ? '执行时间线' : null,
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
