import 'package:flutter/material.dart';

import '../../widgets/glass_surface.dart';

class NewScheduleDialog extends StatefulWidget {
  const NewScheduleDialog({
    super.key,
    required this.initialGoal,
    this.exampleTitle,
    this.fixedTime = false,
  });

  final String initialGoal;
  final String? exampleTitle;
  final bool fixedTime;

  @override
  State<NewScheduleDialog> createState() => _NewScheduleDialogState();
}

class _NewScheduleDialogState extends State<NewScheduleDialog> {
  late final _goal = TextEditingController(text: widget.initialGoal);
  TimeOfDay _time = const TimeOfDay(hour: 9, minute: 0);

  @override
  void dispose() {
    _goal.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaeveGlassDialog(
    title: Text(widget.exampleTitle ?? '新建定时任务'),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (widget.exampleTitle != null)
          Text(widget.initialGoal)
        else
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
          onPressed: widget.fixedTime
              ? null
              : () async {
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
