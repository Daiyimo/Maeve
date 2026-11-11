import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../bridge/maeve_bridge.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../design/maeve_motifs.dart';
import '../common/app_page.dart';
import '../common/status.dart';
import 'settings_components.dart';

/// Read the same packaged notices used by Android; no parallel disclosure list.
class LicensesPage extends StatefulWidget {
  const LicensesPage({super.key});
  @override
  State<LicensesPage> createState() => _LicensesPageState();
}

class _LicensesPageState extends State<LicensesPage> {
  late Future<Map<String, dynamic>> _data = _load();

  Future<Map<String, dynamic>> _load() async {
    final host = await MaeveBridge.methods.invokeMapMethod<String, dynamic>(
      'legal_notices',
    );
    if (host == null) throw StateError('No bundled notices returned');
    final framework = await LicenseRegistry.licenses.toList();
    return {...host, 'framework': framework};
  }

  void _open(String title, String text) {
    AppPage.open(
      context,
      AppPage.list(
        title: title,
        children: [
          const SizedBox(height: 16),
          SelectableText(
            text,
            style: MaeveType.body.copyWith(
              color: MaeveTheme.of(context).colors.ink,
              height: 1.7,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
    future: _data,
    builder: (context, snapshot) {
      final data = snapshot.data;
      return AppPage.list(
        title: '开源许可',
        subtitle: '致谢每一份让 Maeve 成为可能的工作',
        children: [
          if (snapshot.hasError)
            EmptyView(
              icon: Icons.description_outlined,
              title: '许可说明暂时未能读取',
              body: '说明随安装包保存在本机，请重试。',
              action: FilledButton(
                onPressed: () => setState(() {
                  _data = _load();
                }),
                child: const Text('重试'),
              ),
            )
          else if (data == null)
            const Padding(
              padding: EdgeInsets.all(32),
              child: Center(child: MazeRing(size: 24, indeterminate: true)),
            )
          else ...[
            const GroupLabel(text: '许可全文与声明'),
            for (final doc in data['documents'] as List<dynamic>)
              ListPageRow(
                title: doc['name'] as String,
                trailing: const Icon(Icons.chevron_right_rounded, size: 18),
                onTap: () =>
                    _open(doc['name'] as String, doc['text'] as String),
              ),
            const GroupLabel(text: '源码提供'),
            Text(
              data['sourceOffer'] as String,
              style: MaeveType.caption.copyWith(
                color: MaeveTheme.of(context).colors.ink2,
                height: 1.7,
              ),
            ),
            const GroupLabel(text: '应用与运行环境'),
            for (final entry in data['entries'] as List<dynamic>)
              ListPageRow(
                title: entry['name'] as String,
                subtitle: entry['license'] as String,
                onTap: () => _open(
                  entry['name'] as String,
                  '${entry['license']}\n\n${entry['note']}',
                ),
              ),
            const GroupLabel(text: 'Flutter 组件'),
            for (final entry in data['framework'] as List<LicenseEntry>)
              ListPageRow(
                title: entry.packages.join(' · '),
                onTap: () => _open(
                  entry.packages.join(' · '),
                  entry.paragraphs.map((p) => p.text).join('\n\n'),
                ),
              ),
            const SizedBox(height: 32),
          ],
        ],
      );
    },
  );
}
