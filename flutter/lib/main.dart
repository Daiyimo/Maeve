import 'dart:async';

import 'package:flutter/material.dart';

import 'design/maeve_surfaces.dart';
import 'design/maeve_tokens.dart';
import 'models/conversation.dart';
import 'widgets/glass_surface.dart';
import 'widgets/maeve_mark.dart';

export 'bridge/maeve_bridge.dart';

void main() => runApp(const MaeveApp());

class MaeveApp extends StatefulWidget {
  const MaeveApp({super.key});

  @override
  State<MaeveApp> createState() => _MaeveAppState();
}

class _MaeveAppState extends State<MaeveApp> {
  ThemeMode mode = ThemeMode.dark;
  bool reduceTransparency = false;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Maeve',
    debugShowCheckedModeBanner: false,
    themeMode: mode,
    theme: maeveTheme(Brightness.light, blurEnabled: !reduceTransparency),
    darkTheme: maeveTheme(Brightness.dark, blurEnabled: !reduceTransparency),
    home: MaeveShell(
      themeMode: mode,
      onTheme: () => setState(
        () => mode = mode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark,
      ),
      reduceTransparency: reduceTransparency,
      onReduceTransparency: () =>
          setState(() => reduceTransparency = !reduceTransparency),
    ),
  );
}

class MaeveShell extends StatefulWidget {
  const MaeveShell({
    super.key,
    required this.onTheme,
    this.themeMode,
    this.reduceTransparency = false,
    required this.onReduceTransparency,
  });
  final VoidCallback onTheme;
  final ThemeMode? themeMode;
  final bool reduceTransparency;
  final VoidCallback onReduceTransparency;

  @override
  State<MaeveShell> createState() => _MaeveShellState();
}

class _MaeveShellState extends State<MaeveShell> {
  int page = 0;
  bool drawer = false;
  bool generating = false;
  int responseGeneration = 0;
  int conversationSequence = 3;
  String activeConversation = 'c1';
  String searchQuery = '';
  final input = TextEditingController();
  final searchInput = TextEditingController();
  final memoryInput = TextEditingController();
  final conversations = <Conversation>[
    Conversation(id: 'c1', title: '选择之道'),
    Conversation(id: 'c2', title: '搭一个安静的工作台'),
    Conversation(id: 'c3', title: '新的开始'),
  ];
  final memories = <String>[];

  // Falls back to the newest conversation so a stale id cannot break the build
  // once conversations become deletable.
  Conversation get currentConversation => conversations.firstWhere(
    (conversation) => conversation.id == activeConversation,
    orElse: () => conversations.first,
  );

  @override
  void dispose() {
    input.dispose();
    searchInput.dispose();
    memoryInput.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final wide = box.maxWidth >= 900;
      return Scaffold(
        body: SafeArea(
          child: Stack(
            children: [
              // 最底层:竖向渐变 + 双光晕。玻璃的透色与顶边高光靠它才看得出来。
              const Positioned.fill(child: MaeveBackdrop()),
              Row(
                children: [
                  if (wide) _rail(context),
                  if (wide && drawer) _sidebar(context),
                  Expanded(child: _content(context, wide: wide)),
                ],
              ),
              if (!wide && drawer) ...[
                Positioned.fill(
                  child: GestureDetector(
                    key: const ValueKey('drawer-scrim'),
                    onTap: () => setState(() => drawer = false),
                    child: ColoredBox(color: MaeveSurfaces.of(context).scrim),
                  ),
                ),
                Positioned(
                  left: 0,
                  top: 0,
                  bottom: 0,
                  child: _sidebar(context),
                ),
              ],
            ],
          ),
        ),
      );
    },
  );

  Widget _rail(BuildContext context) => SizedBox(
    width: MaeveTokens.railWidth,
    child: DecoratedBox(
      decoration: BoxDecoration(
        border: Border(
          right: BorderSide(color: Theme.of(context).dividerColor),
        ),
      ),
      child: Column(
        children: [
          const SizedBox(height: 18),
          const MaeveMark(size: 34),
          const SizedBox(height: 28),
          _railButton(Icons.chat_bubble_outline, '对话', 0),
          _railButton(Icons.bolt_outlined, '运行', 1),
          _railButton(Icons.memory_outlined, '记忆', 2),
          const Spacer(),
          _railButton(Icons.settings_outlined, '设置', 3),
          IconButton(
            tooltip: '切换主题',
            onPressed: widget.onTheme,
            icon: Icon(
              widget.themeMode == ThemeMode.light
                  ? Icons.dark_mode_outlined
                  : Icons.light_mode_outlined,
            ),
          ),
          const SizedBox(height: 14),
        ],
      ),
    ),
  );

  Widget _railButton(IconData icon, String label, int value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: MaeveGlassButton(
      icon: icon,
      tooltip: label,
      prominent: page == value,
      compact: true,
      onPressed: () => setState(() {
        page = value;
        drawer = false;
      }),
    ),
  );

  Widget _sidebar(BuildContext context) {
    final filtered = conversations.where((conversation) {
      final query = searchQuery.trim().toLowerCase();
      if (query.isEmpty) return true;
      return conversation.title.toLowerCase().contains(query) ||
          conversation.messages.any(
            (message) => message.text.toLowerCase().contains(query),
          );
    }).toList()..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));

    return SizedBox(
      width: MaeveTokens.sidebarWidth,
      child: MaeveGlassSurface(
        tier: MaeveGlassTier.sheet,
        borderRadius: BorderRadius.zero,
        padding: const EdgeInsets.fromLTRB(18, 20, 14, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const MaeveMark(size: 28),
                const SizedBox(width: 10),
                Text('MAEVE', style: maeveEyebrow(context)),
                const Spacer(),
                if (MediaQuery.sizeOf(context).width < 900)
                  IconButton(
                    tooltip: '关闭导航',
                    onPressed: () => setState(() => drawer = false),
                    icon: const Icon(Icons.close),
                  ),
              ],
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                key: const ValueKey('new-conversation'),
                onPressed: _newConversation,
                icon: const Icon(Icons.add),
                label: const Text('新对话'),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              key: const ValueKey('conversation-search'),
              controller: searchInput,
              onChanged: (value) => setState(() => searchQuery = value),
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search, size: 19),
                hintText: '搜索对话',
                isDense: true,
              ),
            ),
            const SizedBox(height: 20),
            Text('最近对话', style: maeveEyebrow(context)),
            const SizedBox(height: 8),
            Expanded(
              child: filtered.isEmpty
                  ? Center(
                      child: Text(
                        '没有匹配的对话',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    )
                  : ListView.builder(
                      itemCount: filtered.length,
                      itemBuilder: (context, index) {
                        final conversation = filtered[index];
                        final active = conversation.id == activeConversation;
                        return _sidebarTile(
                          tile: ListTile(
                            dense: true,
                            selected: active,
                            selectedTileColor: Theme.of(context)
                                .colorScheme
                                .primary
                                .withValues(alpha: .12),
                            leading: Icon(
                              active
                                  ? Icons.radio_button_checked
                                  : Icons.chat_bubble_outline,
                              size: 17,
                              color: active
                                  ? Theme.of(context).colorScheme.primary
                                  : null,
                            ),
                            title: Text(
                              conversation.title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            subtitle: Text(
                              _relativeTime(conversation.updatedAt),
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            onTap: () => setState(() {
                              activeConversation = conversation.id;
                              page = 0;
                              drawer = false;
                              responseGeneration++;
                              generating = false;
                            }),
                          ),
                        );
                      },
                    ),
            ),
            const Divider(),
            _sidebarDestination(Icons.bolt_outlined, '运行', 1),
            _sidebarDestination(Icons.memory_outlined, '记忆', 2),
            _sidebarDestination(Icons.settings_outlined, '设置', 3),
            const SizedBox(height: 10),
            Text(
              'Flutter 界面 · Android 能力',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }

  Widget _sidebarDestination(IconData icon, String label, int value) =>
      _sidebarTile(
        tile: ListTile(
          dense: true,
          leading: Icon(icon, size: 19),
          title: Text(label),
          selected: page == value,
          onTap: () => setState(() {
            page = value;
            drawer = false;
          }),
        ),
      );

  Widget _content(BuildContext context, {required bool wide}) => Column(
    children: [
      _topbar(context, wide: wide),
      Expanded(
        child: switch (page) {
          0 => _chat(context),
          1 => _runs(context),
          2 => _memory(context),
          _ => _settings(context),
        },
      ),
      if (page == 0) _composer(context),
    ],
  );

  Widget _topbar(BuildContext context, {required bool wide}) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 10, 16, 10),
    child: Row(
      children: [
        if (!wide || !drawer)
          IconButton(
            tooltip: wide ? '打开工作区' : '打开对话',
            onPressed: () => setState(() => drawer = !drawer),
            icon: Icon(wide ? Icons.menu_open : Icons.menu),
          ),
        const SizedBox(width: 4),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                page == 0 ? '对话' : _pageEyebrow,
                style: maeveEyebrow(context),
              ),
              const SizedBox(height: 2),
              Text(
                page == 0 ? currentConversation.title : _pageTitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ],
          ),
        ),
        if (page == 0)
          MaeveGlassButton(
            icon: Icons.add,
            tooltip: '新对话',
            onPressed: _newConversation,
          ),
      ],
    ),
  );

  String get _pageTitle => ['聊天', '运行', '记忆', '设置'][page];
  String get _pageEyebrow => ['工作区', '活动', '个人上下文', '偏好'][page];

  Widget _chat(BuildContext context) {
    final messages = currentConversation.messages;
    final width = MediaQuery.sizeOf(context).width;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          maxWidth: MaeveTokens.contentMaxWidth,
        ),
        child: ListView(
          key: const ValueKey('chat-list'),
          padding: EdgeInsets.fromLTRB(
            width < 640 ? 18 : MaeveTokens.pagePadding,
            22,
            width < 640 ? 18 : MaeveTokens.pagePadding,
            24,
          ),
          children: [
            if (messages.isEmpty) ...[
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  const MaeveMark(size: 58, state: MarkState.armed),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('有声音的宿主', style: maeveEyebrow(context)),
                        const SizedBox(height: 5),
                        Text(
                          '一个念头，从这里开始。',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 22),
              Text(
                'Maeve 已准备好陪你梳理想法、探索问题，或规划下一步。',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 22),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _suggestion(context, '帮我理清一个决定'),
                  _suggestion(context, '规划一个项目'),
                ],
              ),
            ] else ...[
              for (final message in messages) ...[
                if (message.isUser)
                  Align(
                    alignment: Alignment.centerRight,
                    child: MaeveGlassSurface(
                      tier: MaeveGlassTier.pill,
                      tint: Theme.of(context).colorScheme.primary,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 12,
                      ),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 560),
                        child: Text(
                          message.text,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.onSurface,
                          ),
                        ),
                      ),
                    ),
                  )
                else
                  _answer(context, message.text),
                const SizedBox(height: 20),
              ],
              if (generating) _thinking(context),
            ],
          ],
        ),
      ),
    );
  }

  Widget _suggestion(BuildContext context, String text) => ActionChip(
    label: Text(text),
    avatar: const Icon(Icons.arrow_outward, size: 15),
    onPressed: () {
      input.text = text;
      _send();
    },
  );

  Widget _answer(BuildContext context, String text) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const MaeveMark(size: 24),
      const SizedBox(width: 12),
      Expanded(
        child: MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('MAEVE', style: maeveEyebrow(context)),
              const SizedBox(height: 8),
              Text(text, style: Theme.of(context).textTheme.bodyLarge),
            ],
          ),
        ),
      ),
    ],
  );

  Widget _thinking(BuildContext context) => Row(
    children: [
      const SizedBox(
        width: 24,
        height: 24,
        child: CircularProgressIndicator(strokeWidth: 2),
      ),
      const SizedBox(width: 12),
      Text('Maeve 正在思考', style: Theme.of(context).textTheme.bodySmall),
    ],
  );

  Widget _composer(BuildContext context) {
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 6, 16, keyboard > 0 ? 8 : 16),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: MaeveTokens.contentMaxWidth,
          ),
          child: MaeveGlassSurface(
            tier: MaeveGlassTier.chrome,
            padding: const EdgeInsets.fromLTRB(12, 6, 6, 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                IconButton(
                  tooltip: '添加内容',
                  onPressed: () => _showNotice('附件将通过 Android 平台桥接接入。'),
                  icon: const Icon(Icons.add),
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('message-input'),
                    controller: input,
                    minLines: 1,
                    maxLines: 6,
                    textInputAction: TextInputAction.newline,
                    decoration: const InputDecoration(
                      hintText: '给 Maeve 一点可以着手的内容…',
                      border: InputBorder.none,
                      filled: false,
                    ),
                    onSubmitted: (_) => _send(),
                  ),
                ),
                const SizedBox(width: 6),
                MaeveGlassButton(
                  key: const ValueKey('send-message'),
                  icon: generating
                      ? Icons.stop_rounded
                      : Icons.arrow_upward_rounded,
                  tooltip: generating ? '停止生成' : '发送消息',
                  prominent: true,
                  onPressed: generating ? _stopGeneration : _send,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _runs(BuildContext context) => _pageBody(
    context,
    eyebrow: '运行记录',
    title: '运行',
    description: 'Agent 活动与任务进度会显示在这里。',
    icon: Icons.bolt_outlined,
    accent: MaeveTokens.success,
    children: [
      MaeveGlassSurface(
        tier: MaeveGlassTier.card,
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            const Icon(Icons.check_circle_outline, color: MaeveTokens.success),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '可以开始第一次运行',
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '还没有记录任何 Agent 活动。',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            Text('就绪', style: maeveEyebrow(context)),
          ],
        ),
      ),
      const SizedBox(height: 12),
      MaeveGlassSurface(
        tier: MaeveGlassTier.card,
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            Icon(
              Icons.info_outline,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                '运行记录目前是视觉原型，尚未接入持久化执行。',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
          ],
        ),
      ),
    ],
  );

  Widget _memory(BuildContext context) => _pageBody(
    context,
    eyebrow: '个人上下文',
    title: '记忆',
    description: '把有用的上下文留在 Maeve 身边。',
    icon: Icons.memory_outlined,
    accent: Theme.of(context).colorScheme.primary,
    trailing: IconButton(
      key: const ValueKey('add-memory'),
      tooltip: '添加记忆',
      onPressed: _addMemory,
      icon: const Icon(Icons.add),
    ),
    children: [
      if (memories.isEmpty)
        MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          padding: const EdgeInsets.all(22),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.bookmark_border, size: 25),
              const SizedBox(height: 14),
              Text('还没有保存任何内容', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 6),
              Text(
                '写一条便签，记住这个会话里的偏好或项目细节。',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 18),
              OutlinedButton.icon(
                onPressed: _addMemory,
                icon: const Icon(Icons.add),
                label: const Text('添加一条记忆'),
              ),
            ],
          ),
        )
      else
        for (final memory in memories)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: MaeveGlassSurface(
              tier: MaeveGlassTier.card,
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Icon(Icons.bookmark_outline),
                  const SizedBox(width: 12),
                  Expanded(child: Text(memory)),
                  IconButton(
                    tooltip: '删除记忆',
                    onPressed: () => setState(() => memories.remove(memory)),
                    icon: const Icon(Icons.delete_outline),
                  ),
                ],
              ),
            ),
          ),
      const SizedBox(height: 12),
      Text(
        '记忆只存在于本次 Flutter 会话，尚未写入磁盘。',
        style: Theme.of(context).textTheme.bodySmall,
      ),
    ],
  );

  Widget _settings(BuildContext context) => _pageBody(
    context,
    eyebrow: '偏好',
    title: '设置',
    description: '调整 Maeve 的观感与回应方式。',
    icon: Icons.tune_outlined,
    accent: Theme.of(context).colorScheme.primary,
    children: [
      MaeveGlassSurface(
        tier: MaeveGlassTier.card,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        child: Material(
          type: MaterialType.transparency,
          child: Column(
            children: [
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('浅色外观'),
                subtitle: const Text('使用清爽的浅色工作台配色。'),
                value: widget.themeMode == ThemeMode.light,
                onChanged: (_) => widget.onTheme(),
              ),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('减少透明效果'),
                subtitle: const Text('关闭背景模糊,玻璃改用近乎不透明的填充。'),
                value: widget.reduceTransparency,
                onChanged: (_) => widget.onReduceTransparency(),
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 12),
      MaeveGlassSurface(
        tier: MaeveGlassTier.card,
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('平台', style: maeveEyebrow(context)),
            const SizedBox(height: 10),
            const Text('Flutter 界面 · Android 能力'),
            const SizedBox(height: 8),
            Text(
              '输入条与侧栏使用有界背景模糊(采样不越出形状边界, iOS 同款);「减少透明效果」或系统高对比度时自动降级为不透明填充。',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
      const SizedBox(height: 12),
      MaeveGlassSurface(
        tier: MaeveGlassTier.card,
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            const MaeveMark(size: 30),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Maeve', style: Theme.of(context).textTheme.titleSmall),
                  Text('有声音的宿主', style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
            const Text('界面原型'),
          ],
        ),
      ),
    ],
  );

  Widget _pageBody(
    BuildContext context, {
    required String eyebrow,
    required String title,
    required String description,
    required IconData icon,
    required Color accent,
    Widget? trailing,
    required List<Widget> children,
  }) {
    final width = MediaQuery.sizeOf(context).width;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          maxWidth: MaeveTokens.contentMaxWidth,
        ),
        child: ListView(
          padding: EdgeInsets.all(width < 640 ? 18 : MaeveTokens.pagePadding),
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                MaeveGlassSurface(
                  tier: MaeveGlassTier.pill,
                  padding: const EdgeInsets.all(12),
                  tint: accent,
                  child: Icon(
                    icon,
                    color: Theme.of(context).colorScheme.onSurface,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(eyebrow, style: maeveEyebrow(context)),
                      const SizedBox(height: 5),
                      Text(
                        title,
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        description,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
                ?trailing,
              ],
            ),
            const SizedBox(height: 22),
            ...children,
          ],
        ),
      ),
    );
  }

  /// ListTiles inside the drawer paint selection and ink onto the nearest
  /// Material, so each row owns a transparent Material to keep them visible.
  Widget _sidebarTile({required Widget tile}) =>
      Material(type: MaterialType.transparency, child: tile);

  void _newConversation() {
    setState(() {
      responseGeneration++;
      generating = false;
      final id = 'c${++conversationSequence}';
      conversations.insert(0, Conversation(id: id, title: '新对话'));
      activeConversation = id;
      page = 0;
      drawer = false;
      searchInput.clear();
      searchQuery = '';
    });
  }

  void _send() {
    final text = input.text.trim();
    if (text.isEmpty || generating) return;
    FocusManager.instance.primaryFocus?.unfocus();
    final generation = ++responseGeneration;
    final conversationId = activeConversation;
    setState(() {
      final conversation = currentConversation;
      conversation.messages.add(Message(text, isUser: true));
      if (conversation.title == '新对话') {
        conversation.title = text.length > 34
            ? '${text.substring(0, 34)}…'
            : text;
      }
      conversation.updatedAt = DateTime.now();
      generating = true;
      input.clear();
    });

    unawaited(_appendDemoReply(generation, conversationId));
  }

  Future<void> _appendDemoReply(int generation, String conversationId) async {
    await Future<void>.delayed(const Duration(milliseconds: 500));
    final index = conversations.indexWhere((item) => item.id == conversationId);
    if (!mounted || generation != responseGeneration || !generating) return;
    if (index < 0) return;
    setState(() {
      final conversation = conversations[index];
      conversation.messages.add(Message('我收到了，接下来交给你。', isUser: false));
      conversation.updatedAt = DateTime.now();
      generating = false;
    });
  }

  void _stopGeneration() {
    setState(() {
      responseGeneration++;
      generating = false;
    });
  }

  void _addMemory() {
    memoryInput.clear();
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('添加一条记忆'),
        content: TextField(
          key: const ValueKey('memory-input'),
          controller: memoryInput,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(hintText: '写一条有用的便签'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              final value = memoryInput.text.trim();
              if (value.isNotEmpty) setState(() => memories.insert(0, value));
              Navigator.pop(context);
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }

  void _showNotice(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String _relativeTime(DateTime time) {
    final difference = DateTime.now().difference(time);
    if (difference.inMinutes < 1) return '刚刚';
    if (difference.inHours < 1) return '${difference.inMinutes} 分钟前';
    if (difference.inDays < 1) return '${difference.inHours} 小时前';
    if (difference.inDays == 1) return '昨天';
    return '${difference.inDays} 天前';
  }
}
