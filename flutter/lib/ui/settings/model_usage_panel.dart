import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../models/token_usage.dart';
import '../../widgets/token_usage_view.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../widgets/glass_surface.dart';

class ModelUsagePanel extends StatefulWidget {
  const ModelUsagePanel({super.key});
  @override
  State<ModelUsagePanel> createState() => _ModelUsagePanelState();
}

class _ModelUsagePanelState extends State<ModelUsagePanel>
    with WidgetsBindingObserver {
  Map<String, dynamic>? _usage;
  String? _error;
  bool _loading = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final usage = await MaeveScope.read(context).settingsBridge.modelUsage();
      if (mounted) setState(() => _usage = usage);
    } catch (_) {
      if (mounted) setState(() => _error = '用量暂时未能读取，请重试。');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final rows = _usage?['models'] as List? ?? const [];
    return MaeveGlassSurface(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '近 30 天 Token 用量',
                  style: MaeveType.bodyStrong.copyWith(color: c.ink),
                ),
              ),
              IconButton(
                tooltip: '刷新 Token 用量',
                onPressed: _loading ? null : _load,
                icon: const Icon(Icons.refresh_rounded, size: 20),
              ),
            ],
          ),
          if (_usage != null) ...[
            TokenUsageView(
              usage: TokenUsage.fromMap(_usage!['totals'] as Map? ?? {}),
            ),
            const SizedBox(height: 12),
            if (rows.length == 1)
              Text(
                '${rows.single['model']} · ${rows.single['provider']}',
                style: MaeveType.caption.copyWith(color: c.ink2),
              ),
            for (final row in rows.length > 1 ? rows : const [])
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${row['model']} · ${row['provider']}',
                      style: MaeveType.caption.copyWith(color: c.ink2),
                    ),
                    const SizedBox(height: 6),
                    TokenUsageView(
                      usage: TokenUsage.fromMap(row['totals'] as Map),
                      compact: true,
                    ),
                  ],
                ),
              ),
            const SizedBox(height: 14),
            Text(
              '本机请求记录，含辅助调用。缓存已计入输入；服务商未返回的用量不估算。',
              style: MaeveType.hint.copyWith(color: c.ink3, height: 1.5),
            ),
          ] else if (_loading)
            Text('正在读取用量…', style: MaeveType.caption.copyWith(color: c.ink2)),
          if (_error != null)
            Text(_error!, style: MaeveType.caption.copyWith(color: c.bad)),
        ],
      ),
    );
  }
}
