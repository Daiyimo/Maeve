import 'package:flutter/material.dart';

import '../../bridge/maeve_chat_bridge.dart';
import '../../app/maeve_controller.dart';
import '../../widgets/execution_view.dart';
import '../common/app_page.dart';
import 'settings_components.dart';

class RunHistoryPage extends StatefulWidget {
  const RunHistoryPage({super.key, this.bridge});
  final MaeveChatBridge? bridge;
  @override
  State<RunHistoryPage> createState() => _RunHistoryPageState();
}

class _RunHistoryPageState extends State<RunHistoryPage> {
  late final MaeveChatBridge _bridge =
      widget.bridge ?? MaeveScope.read(context).chatBridge;
  List<Map<String, dynamic>>? _rows;
  String? _error;
  bool _loading = false;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    try {
      final rows = await _bridge.runs();
      if (mounted) {
        setState(() {
          _rows = rows;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _error = '未能读取本机记录，请重试。');
    } finally {
      _loading = false;
    }
  }

  String _date(dynamic value) {
    if (value is! num) return '时间未记录';
    final d = DateTime.fromMillisecondsSinceEpoch(value.toInt());
    String two(int n) => n.toString().padLeft(2, '0');
    return '${d.year}-${two(d.month)}-${two(d.day)} ${two(d.hour)}:${two(d.minute)}';
  }

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '执行记录',
    subtitle: '最近保存在本机的真实任务',
    actions: [
      IconButton(
        tooltip: '刷新执行记录列表',
        onPressed: _load,
        icon: const Icon(Icons.refresh_rounded),
      ),
    ],
    children: [
      const SizedBox(height: 20),
      if (_error != null)
        SettingsNotice(text: _error!, icon: Icons.info_outline),
      if (_rows == null && _error == null) const Text('正在读取本机记录…'),
      if (_rows?.isEmpty == true) const Text('还没有执行记录'),
      if (_rows?.isNotEmpty == true)
        SettingsSection(
          title: '运行历史',
          rows: [
            for (final row in _rows!)
              if (row['id'] is String)
                SettingsTile(
                  icon: Icons.receipt_long_outlined,
                  title: row['goal'] as String? ?? '未命名任务',
                  subtitle: _date(row['startedAtMs']),
                  value: switch (row['status']) {
                    'running' => '进行中',
                    'succeeded' => '已完成',
                    'failed' => '失败',
                    'cancelled' => '已停止',
                    'unverified' => '未核实',
                    'error' => '出错',
                    'interrupted' => '已中断',
                    _ => '状态未知',
                  },
                  onTap: () => AppPage.open(
                    context,
                    RunDetailsPage(
                      bridge: _bridge,
                      runId: row['id'] as String,
                      follow: row['status'] == 'running',
                    ),
                  ),
                ),
          ],
        ),
    ],
  );
}
