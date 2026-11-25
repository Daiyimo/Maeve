import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../widgets/glass_surface.dart';
import '../common/app_page.dart';
import 'settings_components.dart';

class ScheduledTasksPage extends StatefulWidget {
  const ScheduledTasksPage({super.key, this.conversationId, this.initialGoal});

  final String? conversationId;
  final String? initialGoal;

  @override
  State<ScheduledTasksPage> createState() => _ScheduledTasksPageState();
}

class _ScheduledTasksPageState extends State<ScheduledTasksPage>
    with WidgetsBindingObserver {
  Map<String, dynamic>? _snapshot;
  bool _busy = false;
  bool _reading = false;
  int _revision = 0;
  String? _error;
  String? _notice;
  Timer? _refresh;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_update('snapshot'));
      final goal = widget.initialGoal;
      if (goal != null && mounted) unawaited(_create(goal: goal));
    });
    _refresh = Timer.periodic(const Duration(seconds: 3), (_) {
      if (mounted && ModalRoute.of(context)?.isCurrent == true) {
        unawaited(_update('snapshot', quiet: true));
      }
    });
  }

  @override
  void dispose() {
    _refresh?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_update('snapshot'));
  }

  Future<void> _update(
    String action, {
    Map<String, Object> args = const {},
    bool quiet = false,
  }) async {
    final reading = action == 'snapshot';
    if (!mounted || _busy || (reading && _reading)) return;
    if (reading) {
      _reading = true;
    } else {
      _revision++;
      _busy = true;
    }
    final revision = _revision;
    if (!quiet) setState(() => _error = null);
    try {
      final bridge = MaeveScope.read(context).settingsBridge;
      final value = await bridge.schedules({'action': action, ...args});
      if (!mounted || revision != _revision) return;
      setState(() {
        _snapshot = value;
        if (action == 'runNow') {
          _notice = value['deferred'] == null ? '已发起本次执行' : '已暂缓，解锁并恢复网络后执行';
        } else if (action != 'snapshot') {
          _notice = null;
        }
      });
    } catch (error) {
      if (mounted && !quiet && revision == _revision) {
        setState(() {
          _error = error is PlatformException
              ? error.message ?? '定时任务操作未完成'
              : '定时任务暂不可用';
        });
      }
    } finally {
      if (reading) {
        _reading = false;
      } else {
        _busy = false;
      }
      if (mounted && !quiet) setState(() {});
    }
  }

  Future<void> _create({String? goal}) async {
    final input = await showDialog<(String, TimeOfDay)>(
      context: context,
      builder: (_) => _NewScheduleDialog(initialGoal: goal ?? ''),
    );
    if (!mounted || input == null) return;
    final (task, time) = input;
    String two(int value) => value.toString().padLeft(2, '0');
    await _update(
      'create',
      args: {
        'goal': task,
        'time': '${two(time.hour)}:${two(time.minute)}',
        'conversationId': ?widget.conversationId,
      },
    );
  }

  Future<void> _remove(String id) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => MaeveGlassDialog(
        title: const Text('删除这个定时任务？'),
        content: const Text('之后的计划和暂缓的执行会取消。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed == true) await _update('remove', args: {'id': id});
  }

  String _status(Map plan) {
    final date = DateTime.fromMillisecondsSinceEpoch(plan['nextAt'] as int);
    String two(int value) => value.toString().padLeft(2, '0');
    final when = plan['time'] == null
        ? '${date.month}月${date.day}日 ${two(date.hour)}:${two(date.minute)}'
        : '每日 ${plan['time']}';
    final status = switch (plan['state']) {
      'waiting' => switch (plan['blocked']) {
        'locked' => '等待手机解锁',
        'offline' => '等待网络恢复',
        _ => '等待执行',
      },
      'Dispatched' || 'ActionDispatched' || 'Claimed' => '执行中',
      'Delivered' => '上次已完成',
      'Failed' => '上次未完成',
      'Unknown' => '上次结果待核实',
      _ => plan['enabled'] == true ? '已启用' : '已暂停',
    };
    return '$when · $status';
  }

  @override
  Widget build(BuildContext context) {
    final plans =
        (_snapshot?['plans'] as List?)?.whereType<Map>() ?? const <Map>[];
    return AppPage.list(
      title: '定时任务',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: _busy ? null : () => _update('snapshot'),
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
      children: [
        const SizedBox(height: 20),
        FilledButton.icon(
          onPressed: _busy ? null : _create,
          icon: const Icon(Icons.add_rounded),
          label: const Text('新建定时任务'),
        ),
        if (_error != null)
          SettingsNotice(text: _error!, icon: Icons.error_outline),
        if (_notice != null)
          SettingsNotice(text: _notice!, icon: Icons.info_outline),
        if (_snapshot?['exactAlarm'] == false)
          SettingsTile(
            title: '允许准时提醒',
            subtitle: '系统授权后，锁屏期间也能按时检查执行条件',
            icon: Icons.alarm_on_outlined,
            onTap: () =>
                MaeveScope.read(context).settingsBridge
                    .openAccess('exactAlarm'),
          ),
        if (_snapshot == null && _error == null)
          const Center(child: CircularProgressIndicator()),
        if (_snapshot != null && plans.isEmpty)
          const FooterNote(text: '还没有定时任务。可以从每日签到开始。'),
        for (final plan in plans)
          SettingsSection(
            title: _status(plan),
            rows: [
              SettingsTile(
                title: plan['goal'] as String,
                icon: Icons.schedule_rounded,
                trailing: Semantics(
                  label: '启用定时任务',
                  child: Switch(
                    value: plan['enabled'] == true,
                    onChanged: _busy
                        ? null
                        : (value) => _update(
                            'setEnabled',
                            args: {
                              'id': plan['id'] as String,
                              'enabled': value,
                            },
                          ),
                  ),
                ),
              ),
              SettingsTile(
                title: '立即执行',
                icon: Icons.play_arrow_rounded,
                enabled: !_busy && plan['inFlight'] != true,
                onTap: () =>
                    _update('runNow', args: {'id': plan['id'] as String}),
              ),
              SettingsTile(
                title: '删除计划',
                icon: Icons.delete_outline,
                enabled: !_busy,
                onTap: () => _remove(plan['id'] as String),
              ),
            ],
          ),
      ],
    );
  }
}

class _NewScheduleDialog extends StatefulWidget {
  const _NewScheduleDialog({required this.initialGoal});

  final String initialGoal;

  @override
  State<_NewScheduleDialog> createState() => _NewScheduleDialogState();
}

class _NewScheduleDialogState extends State<_NewScheduleDialog> {
  late final _goal = TextEditingController(text: widget.initialGoal);
  TimeOfDay _time = const TimeOfDay(hour: 9, minute: 0);

  @override
  void dispose() {
    _goal.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaeveGlassDialog(
    title: const Text('新建定时任务'),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TextField(
          controller: _goal,
          maxLength: 4000,
          maxLines: 3,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            labelText: '任务说明',
            hintText: '例如：打开指定应用并完成签到',
          ),
        ),
        TextButton.icon(
          icon: const Icon(Icons.access_time_rounded),
          label: Text('每日 ${_time.format(context)}'),
          onPressed: () async {
            final value = await showTimePicker(
              context: context,
              initialTime: _time,
            );
            if (mounted && value != null) setState(() => _time = value);
          },
        ),
      ],
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('取消'),
      ),
      FilledButton(
        onPressed: _goal.text.trim().isEmpty
            ? null
            : () => Navigator.pop(context, (_goal.text.trim(), _time)),
        child: const Text('启用'),
      ),
    ],
  );
}
