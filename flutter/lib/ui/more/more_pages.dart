import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_tokens.dart';
import '../common/app_page.dart';
import '../common/app_sheet.dart';
import '../common/status.dart';
import '../common/pressable.dart';
import '../settings/settings_components.dart';

/// B11 网页版:地址栏 + 内容区。真正的 WebView 由 Android 侧提供。
class WebViewPage extends StatelessWidget {
  const WebViewPage({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AppPage(
      title: '网页版',
      subtitle: '同一条会话，在浏览器里继续',
      padded: false,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(
              MaeveSpace.gutter,
              MaeveLayout.pageBarHeight + MaeveSpace.sm,
              MaeveSpace.gutter,
              MaeveSpace.sm,
            ),
            child: Row(
              children: [
                Expanded(
                  child: Container(
                    height: 40,
                    padding: const EdgeInsets.symmetric(
                      horizontal: MaeveSpace.md,
                    ),
                    decoration: BoxDecoration(
                      color: scheme.onSurface.withValues(alpha: .05),
                      borderRadius: BorderRadius.circular(MaeveRadius.pill),
                      border: Border.all(
                        color: scheme.outline.withValues(alpha: .5),
                        width: .5,
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.lock_outline_rounded,
                          size: 14,
                          color: scheme.onSurfaceVariant,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            'maeve.local/session',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: MaeveFonts.mono(
                              context,
                              size: 12,
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: MaeveSpace.sm),
                Pressable(
                  onTap: () => _notice(context, '刷新将由 WebView 桥接提供。'),
                  borderRadius: BorderRadius.circular(MaeveRadius.pill),
                  semanticLabel: '刷新',
                  child: SizedBox.square(
                    dimension: 40,
                    child: Icon(
                      Icons.refresh_rounded,
                      size: 18,
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: const EmptyView(
                  icon: Icons.language_rounded,
                  title: 'WebView 还没接上',
                  body:
                      '这一页的壳已经按设计做好了：地址栏、进度、手势返回。'
                      '真正的网页内容由 Android 侧的 WebView 提供，接上后这里直接显示。',
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// B11 终端:独立主题(固定深底 + 等宽),命令执行由 Android 侧提供。
class TerminalPage extends StatefulWidget {
  const TerminalPage({super.key});

  @override
  State<TerminalPage> createState() => _TerminalPageState();
}

class _TerminalPageState extends State<TerminalPage> {
  final List<({String text, bool isCommand})> _lines = [
    (text: 'Maeve shell · 本地预览', isCommand: false),
    (text: '命令执行由 Android 沙箱提供，这里先给出终端的视觉与输入。', isCommand: false),
  ];
  final TextEditingController _input = TextEditingController();

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  void _submit() {
    final text = _input.text.trim();
    if (text.isEmpty) return;
    setState(() {
      _lines.add((text: text, isCommand: true));
      _lines.add((text: '未连接执行层：$text 没有真的跑。', isCommand: false));
      _input.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    // 终端有自己的主题:固定深底,不跟配色走。
    const background = Color(0xFF0B0F0D);
    const foreground = Color(0xFFD7E6DB);
    const accent = Color(0xFF7FD1A6);
    return Theme(
      data: Theme.of(context).copyWith(scaffoldBackgroundColor: background),
      child: Scaffold(
        backgroundColor: background,
        body: SafeArea(
          child: Column(
            children: [
              SizedBox(
                height: MaeveLayout.pageBarHeight,
                child: Row(
                  children: [
                    SizedBox(
                      width: MaeveLayout.touch,
                      child: PageBackButton(
                        onPressed: () => Navigator.of(context).maybePop(),
                      ),
                    ),
                    Expanded(
                      child: Text(
                        '终端',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(color: foreground),
                      ),
                    ),
                    const SizedBox(width: MaeveLayout.touch),
                  ],
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(MaeveSpace.lg),
                  children: [
                    for (final line in _lines)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: Text(
                          line.isCommand ? '\$ ${line.text}' : line.text,
                          style: MaeveFonts.mono(
                            context,
                            size: 12.5,
                            color: line.isCommand ? accent : foreground,
                            height: 1.5,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.fromLTRB(
                  MaeveSpace.lg,
                  MaeveSpace.sm,
                  MaeveSpace.lg,
                  MaeveSpace.lg,
                ),
                decoration: const BoxDecoration(
                  border: Border(
                    top: BorderSide(color: Color(0x1FFFFFFF), width: .5),
                  ),
                ),
                child: Row(
                  children: [
                    Text(
                      '\$',
                      style: MaeveFonts.mono(context, color: accent, size: 13),
                    ),
                    const SizedBox(width: MaeveSpace.sm),
                    Expanded(
                      child: TextField(
                        key: const ValueKey('terminal-input'),
                        controller: _input,
                        onSubmitted: (_) => _submit(),
                        style: MaeveFonts.mono(
                          context,
                          size: 12.5,
                          color: foreground,
                        ),
                        cursorColor: accent,
                        decoration: InputDecoration(
                          isDense: true,
                          border: InputBorder.none,
                          hintText: '输入命令…',
                          hintStyle: MaeveFonts.mono(
                            context,
                            size: 12.5,
                            color: foreground.withValues(alpha: .35),
                          ),
                        ),
                      ),
                    ),
                    Pressable(
                      onTap: _submit,
                      borderRadius: BorderRadius.circular(MaeveRadius.pill),
                      semanticLabel: '执行',
                      child: SizedBox.square(
                        dimension: 36,
                        child: Icon(
                          Icons.keyboard_return_rounded,
                          size: 18,
                          color: accent,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// B11 社区:外部入口。没有 url_launcher,点击先说明要走桥。
class CommunityPage extends StatelessWidget {
  const CommunityPage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '社区',
    subtitle: '问题、想法、更新',
    children: [
      SettingsSection(
        title: '去处',
        rows: [
          SettingsTile(
            icon: Icons.forum_outlined,
            title: '讨论区',
            subtitle: '提问与经验',
            onTap: () => _notice(context, '打开外部链接需要平台桥接。'),
          ),
          SettingsTile(
            icon: Icons.history_rounded,
            title: '更新日志',
            subtitle: '每个版本改了什么',
            onTap: () => _notice(context, '更新日志会在接入后从云端拉取。'),
          ),
          SettingsTile(
            icon: Icons.bug_report_outlined,
            title: '反馈问题',
            subtitle: '附上诊断信息会更快',
            onTap: () => _notice(context, '反馈通道需要平台桥接。'),
          ),
        ],
        footnote: '社区入口只跳转，不上传任何会话内容。',
      ),
    ],
  );
}

/// 分享:复制全文/Markdown 是真能用的,系统分享要接桥。
Future<void> showShareSheet(BuildContext context, MaeveController controller) {
  final conversation = controller.active;
  final transcript = conversation.messages
      .map((message) => '${message.isUser ? '我' : 'Maeve'}: ${message.text}')
      .join('\n\n');
  return showMaeveSheet<void>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SheetTitleBar(
            title: '分享这段对话',
            subtitle: '${conversation.messages.length} 条 · 只在本机处理',
          ),
          const SizedBox(height: MaeveSpace.sm),
          ListPageRow(
            title: '复制全文',
            subtitle: '纯文本，粘到哪里都能读',
            leading: const Icon(Icons.copy_all_outlined, size: 19),
            onTap: () async {
              await Clipboard.setData(ClipboardData(text: transcript));
              if (context.mounted) {
                Navigator.of(context).pop();
                _notice(context, '已复制全文。');
              }
            },
          ),
          ListPageRow(
            title: '复制为 Markdown',
            subtitle: '标题 + 分段，适合贴进笔记',
            leading: const Icon(Icons.code_rounded, size: 19),
            onTap: () async {
              final markdown = '# ${conversation.title}\n\n$transcript';
              await Clipboard.setData(ClipboardData(text: markdown));
              if (context.mounted) {
                Navigator.of(context).pop();
                _notice(context, '已复制 Markdown。');
              }
            },
          ),
          ListPageRow(
            title: '系统分享',
            subtitle: '发送到其它应用',
            leading: const Icon(Icons.ios_share_rounded, size: 19),
            onTap: () {
              Navigator.of(context).pop();
              _notice(context, '系统分享面板将通过 Android 平台桥接接入。');
            },
          ),
        ],
      ),
    ),
  );
}

void _notice(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}
