import 'package:flutter/material.dart';

import '../../design/maeve_motifs.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/status.dart';
import '../settings/settings_components.dart';

/// 运行:Agent 活动与进度。原型阶段是视觉就绪态。
///
/// 这一页是"仪表":校准角 + 细线网格 + 钢琴卷,不用装饰图。
class RunsPage extends StatelessWidget {
  const RunsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '运行',
      subtitle: 'Agent 活动与任务进度',
      eyebrow: 'RUNS',
      children: [
        CalibrationCorners(
          child: Container(
            padding: const EdgeInsets.all(MaeveSpace.s16),
            decoration: BoxDecoration(
              color: colors.surface,
              borderRadius: BorderRadius.circular(MaeveRadius.lg),
              border: Border.all(color: colors.hair, width: .5),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      '最近一轮',
                      style: MaeveType.bodyStrong.copyWith(color: colors.ink),
                    ),
                    const Spacer(),
                    StatusChip(
                      label: '就绪',
                      tone: MaeveTone.ready,
                      filled: true,
                    ),
                  ],
                ),
                const SizedBox(height: MaeveSpace.s12),
                const PianoRoll(
                  states: [
                    NoteState.done,
                    NoteState.done,
                    NoteState.active,
                    NoteState.pending,
                    NoteState.pending,
                    NoteState.pending,
                    NoteState.pending,
                    NoteState.pending,
                  ],
                  height: 22,
                ),
                const SizedBox(height: MaeveSpace.s10),
                Text(
                  '还没有开始任何任务。让她替你做一件小事试试。',
                  style: MaeveType.caption.copyWith(color: colors.ink3),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: MaeveSpace.s16),
        SettingsSection(
          title: '会记录什么',
          rows: const [
            SettingsTile(
              icon: Icons.route_outlined,
              title: '每一步的动作',
              subtitle: '点按、输入、切换页面',
              value: '记录',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.screenshot_monitor_outlined,
              title: '屏幕内容',
              subtitle: '只在执行时按需读取，不进记录',
              value: '不留存',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.timer_outlined,
              title: '耗时',
              subtitle: '按步骤统计，不按秒刷屏',
              value: '统计',
              enabled: false,
            ),
          ],
          footnote: '运行记录目前是视觉原型，尚未接入持久化执行。',
        ),
        const SizedBox(height: MaeveSpace.s24),
        Center(
          child: Text(
            'RUNS · LOCAL PREVIEW',
            style: MaeveType.eyebrow.copyWith(color: colors.ink3),
          ),
        ),
      ],
    );
  }
}
