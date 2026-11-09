import 'dart:async';

import 'package:flutter/material.dart';

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

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Maeve',
    debugShowCheckedModeBanner: false,
    themeMode: mode,
    theme: maeveTheme(Brightness.light),
    darkTheme: maeveTheme(Brightness.dark),
    home: MaeveShell(
      themeMode: mode,
      onTheme: () => setState(
        () => mode = mode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark,
      ),
    ),
  );
}

class MaeveShell extends StatefulWidget {
  const MaeveShell({super.key, required this.onTheme, this.themeMode});
  final VoidCallback onTheme;
  final ThemeMode? themeMode;

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
    Conversation(id: 'c1', title: 'The architecture of choice'),
    Conversation(id: 'c2', title: 'Build a calmer workspace'),
    Conversation(id: 'c3', title: 'A new beginning'),
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
                    child: ColoredBox(
                      color: Colors.black.withValues(alpha: .48),
                    ),
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
          _railButton(Icons.chat_bubble_outline, 'Conversations', 0),
          _railButton(Icons.bolt_outlined, 'Runs', 1),
          _railButton(Icons.memory_outlined, 'Memory', 2),
          const Spacer(),
          _railButton(Icons.settings_outlined, 'Settings', 3),
          IconButton(
            tooltip: 'Toggle theme',
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
                    tooltip: 'Close navigation',
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
                label: const Text('New conversation'),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              key: const ValueKey('conversation-search'),
              controller: searchInput,
              onChanged: (value) => setState(() => searchQuery = value),
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search, size: 19),
                hintText: 'Search conversations',
                isDense: true,
              ),
            ),
            const SizedBox(height: 20),
            Text('RECENT CONVERSATIONS', style: maeveEyebrow(context)),
            const SizedBox(height: 8),
            Expanded(
              child: filtered.isEmpty
                  ? Center(
                      child: Text(
                        'No matching conversations',
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
            _sidebarDestination(Icons.bolt_outlined, 'Runs', 1),
            _sidebarDestination(Icons.memory_outlined, 'Memory', 2),
            _sidebarDestination(Icons.settings_outlined, 'Settings', 3),
            const SizedBox(height: 10),
            Text(
              'Flutter interface · Android capabilities',
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
            tooltip: wide ? 'Open workspace' : 'Open conversations',
            onPressed: () => setState(() => drawer = !drawer),
            icon: Icon(wide ? Icons.menu_open : Icons.menu),
          ),
        const SizedBox(width: 4),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                page == 0 ? 'CONVERSATION' : _pageEyebrow,
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
            tooltip: 'New conversation',
            onPressed: _newConversation,
          ),
      ],
    ),
  );

  String get _pageTitle => ['Chat', 'Runs', 'Memory', 'Settings'][page];
  String get _pageEyebrow =>
      ['WORKSPACE', 'ACTIVITY', 'PERSONAL CONTEXT', 'PREFERENCES'][page];

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
                        Text(
                          'A HOST WITH A VOICE',
                          style: maeveEyebrow(context),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          'One thought, from here.',
                          style: Theme.of(context).textTheme.headlineSmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 22),
              Text(
                'Maeve is ready to shape an idea, explore a question, or help plan what comes next.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 22),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _suggestion(context, 'Help me think through a decision'),
                  _suggestion(context, 'Plan a project'),
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
      Text('Maeve is thinking', style: Theme.of(context).textTheme.bodySmall),
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
                  tooltip: 'Add content',
                  onPressed: () => _showNotice(
                    'Attachments will be connected through the Android platform bridge.',
                  ),
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
                      hintText: 'Give Maeve something to work with…',
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
                  tooltip: generating ? 'Stop generation' : 'Send message',
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
    eyebrow: 'RUN HISTORY',
    title: 'Runs',
    description: 'Agent activity and task progress will appear here.',
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
                    'Ready for your first run',
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'No agent activity has been recorded yet.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            Text('READY', style: maeveEyebrow(context)),
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
                'Run history is a visual prototype. Persistent agent execution is not connected yet.',
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
    eyebrow: 'PERSONAL CONTEXT',
    title: 'Memory',
    description: 'Keep useful context close to Maeve.',
    icon: Icons.memory_outlined,
    accent: Theme.of(context).colorScheme.primary,
    trailing: IconButton(
      key: const ValueKey('add-memory'),
      tooltip: 'Add memory',
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
              Text(
                'Nothing saved yet',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 6),
              Text(
                'Add a note to keep a useful preference or project detail in this session.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 18),
              OutlinedButton.icon(
                onPressed: _addMemory,
                icon: const Icon(Icons.add),
                label: const Text('Add a memory'),
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
                    tooltip: 'Delete memory',
                    onPressed: () => setState(() => memories.remove(memory)),
                    icon: const Icon(Icons.delete_outline),
                  ),
                ],
              ),
            ),
          ),
      const SizedBox(height: 12),
      Text(
        'Memories remain in this Flutter session and are not yet stored on disk.',
        style: Theme.of(context).textTheme.bodySmall,
      ),
    ],
  );

  Widget _settings(BuildContext context) => _pageBody(
    context,
    eyebrow: 'PREFERENCES',
    title: 'Settings',
    description: 'Shape how Maeve feels and responds.',
    icon: Icons.tune_outlined,
    accent: Theme.of(context).colorScheme.primary,
    children: [
      MaeveGlassSurface(
        tier: MaeveGlassTier.card,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        child: Material(
          type: MaterialType.transparency,
          child: SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('Light appearance'),
            subtitle: const Text('Use a cool, light workspace palette.'),
            value: widget.themeMode == ThemeMode.light,
            onChanged: (_) => widget.onTheme(),
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
            Text('PLATFORM', style: maeveEyebrow(context)),
            const SizedBox(height: 10),
            const Text('Flutter interface · Android capabilities'),
            const SizedBox(height: 8),
            Text(
              'Glass surfaces use stable layered fills. Runtime backdrop blur is disabled because Android RenderThread instability was observed.',
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
                  Text(
                    'A host with a voice',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const Text('UI prototype'),
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
      conversations.insert(0, Conversation(id: id, title: 'New conversation'));
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
      if (conversation.title == 'New conversation') {
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
      conversation.messages.add(
        Message('I have it. The next move is yours.', isUser: false),
      );
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
        title: const Text('Add a memory'),
        content: TextField(
          key: const ValueKey('memory-input'),
          controller: memoryInput,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(hintText: 'Write a useful note'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final value = memoryInput.text.trim();
              if (value.isNotEmpty) setState(() => memories.insert(0, value));
              Navigator.pop(context);
            },
            child: const Text('Save'),
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
    if (difference.inMinutes < 1) return 'Just now';
    if (difference.inHours < 1) return '${difference.inMinutes}m ago';
    if (difference.inDays < 1) return '${difference.inHours}h ago';
    if (difference.inDays == 1) return 'Yesterday';
    return '${difference.inDays}d ago';
  }
}
