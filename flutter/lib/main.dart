import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

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
    theme: _theme(Brightness.light),
    darkTheme: _theme(Brightness.dark),
    home: MaeveShell(
      onTheme: () => setState(
        () => mode = mode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark,
      ),
    ),
  );
}

ThemeData _theme(Brightness brightness) {
  final dark = brightness == Brightness.dark;
  final scheme = ColorScheme.fromSeed(
    seedColor: const Color(0xffd4a6ff),
    brightness: brightness,
    surface: dark ? const Color(0xff0d0d12) : const Color(0xfff6f5f9),
  );
  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: scheme.surface,
    dividerColor: dark ? const Color(0xff2a2935) : const Color(0xffdddbe5),
    textTheme: Typography.material2021(platform: TargetPlatform.android).white
        .apply(
          bodyColor: dark ? const Color(0xffecebf4) : const Color(0xff22202c),
          displayColor: dark
              ? const Color(0xffecebf4)
              : const Color(0xff22202c),
        ),
  );
}

class MaeveShell extends StatefulWidget {
  const MaeveShell({super.key, required this.onTheme});
  final VoidCallback onTheme;
  @override
  State<MaeveShell> createState() => _MaeveShellState();
}

class _MaeveShellState extends State<MaeveShell> {
  int page = 0;
  bool drawer = false;
  final input = TextEditingController();
  final messages = <String>['Maeve is ready. What should we make real today?'];
  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final wide = box.maxWidth >= 900;
      final sidebar = _sidebar(context);
      return Scaffold(
        body: SafeArea(
          child: Stack(
            children: [
              Row(
                children: [
                  if (wide) _rail(context),
                  if (wide && drawer) sidebar,
                  Expanded(child: _content(context)),
                ],
              ),
              if (!wide && drawer)
                Positioned.fill(
                  child: GestureDetector(
                    onTap: () => setState(() => drawer = false),
                    child: ColoredBox(
                      color: Colors.black.withValues(alpha: .5),
                    ),
                  ),
                ),
              if (!wide && drawer)
                Positioned(left: 0, top: 0, bottom: 0, child: sidebar),
            ],
          ),
        ),
      );
    },
  );

  Widget _rail(BuildContext context) => SizedBox(
    width: 72,
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
            icon: const Icon(Icons.brightness_6_outlined),
          ),
          const SizedBox(height: 14),
        ],
      ),
    ),
  );

  Widget _railButton(IconData icon, String label, int value) => IconButton(
    tooltip: label,
    onPressed: () => setState(() => page = value),
    style: IconButton.styleFrom(
      foregroundColor: page == value ? const Color(0xffd4a6ff) : null,
      backgroundColor: page == value ? const Color(0x1fd4a6ff) : null,
    ),
    icon: Icon(icon),
  );

  Widget _sidebar(BuildContext context) => SizedBox(
    width: 300,
    child: DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        border: Border(
          right: BorderSide(color: Theme.of(context).dividerColor),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 20, 14, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const MaeveMark(size: 28),
                const SizedBox(width: 10),
                Text('MAEVE', style: _eyebrow(context)),
              ],
            ),
            const SizedBox(height: 24),
            TextField(
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search, size: 19),
                hintText: 'Search conversations',
                filled: true,
                fillColor: Theme.of(context).dividerColor.withValues(alpha: .3),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
            const SizedBox(height: 24),
            Text('RECENT LOOPS', style: _eyebrow(context)),
            const SizedBox(height: 8),
            _conversation('The architecture of choice', 'Just now', true),
            _conversation('Build a calmer workspace', 'Yesterday', false),
            _conversation('Untitled conversation', 'Oct 31', false),
            const Spacer(),
            const Row(
              children: [
                CircleAvatar(
                  radius: 16,
                  child: Icon(Icons.person_outline, size: 18),
                ),
                SizedBox(width: 10),
                Expanded(child: Text('Local operator')),
                Icon(Icons.more_horiz),
              ],
            ),
          ],
        ),
      ),
    ),
  );

  Widget _conversation(String title, String time, bool active) => ListTile(
    dense: true,
    contentPadding: const EdgeInsets.symmetric(horizontal: 8),
    tileColor: active ? const Color(0x1fd4a6ff) : null,
    leading: Icon(
      active ? Icons.radio_button_checked : Icons.radio_button_unchecked,
      size: 16,
      color: active ? const Color(0xffd4a6ff) : null,
    ),
    title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
    subtitle: Text(time, style: const TextStyle(fontSize: 11)),
    onTap: () => setState(() => drawer = false),
  );

  Widget _content(BuildContext context) => page == 0
      ? Column(
          children: [
            _topbar(context),
            Expanded(child: _chat(context)),
            _composer(context),
          ],
        )
      : Column(
          children: [
            _topbar(context),
            Expanded(
              child: Center(
                child: Text(
                  ['RUNS', 'MEMORY', 'SETTINGS'][page - 1],
                  style: _eyebrow(context),
                ),
              ),
            ),
          ],
        );

  Widget _topbar(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(18, 14, 18, 12),
    child: Row(
      children: [
        IconButton(
          tooltip: 'Open conversations',
          onPressed: () => setState(() => drawer = true),
          icon: const Icon(Icons.menu),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('LOOP 01', style: _eyebrow(context)),
              const SizedBox(height: 2),
              const Text(
                'The architecture of choice',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
        IconButton(
          tooltip: 'New conversation',
          onPressed: () => setState(messages.clear),
          icon: const Icon(Icons.add),
        ),
      ],
    ),
  );

  Widget _chat(BuildContext context) => ListView(
    padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
    children: [
      Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          const MaeveMark(size: 62, state: MarkState.armed),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('A HOST WITH A VOICE', style: _eyebrow(context)),
              const SizedBox(height: 5),
              Text(
                'Tell me where to begin.',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ],
          ),
        ],
      ),
      const SizedBox(height: 34),
      ...messages.map(
        (text) => Padding(
          padding: const EdgeInsets.only(bottom: 18),
          child: _answer(context, text),
        ),
      ),
    ],
  );

  Widget _answer(BuildContext context, String text) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Container(width: 2, height: 58, color: const Color(0xffd4a6ff)),
      const SizedBox(width: 14),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('MAEVE', style: _eyebrow(context)),
            const SizedBox(height: 7),
            Text(text, style: const TextStyle(fontSize: 16, height: 1.55)),
          ],
        ),
      ),
    ],
  );

  Widget _composer(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(18, 6, 18, 18),
    child: DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest
            .withValues(alpha: .82),
        border: Border.all(color: Theme.of(context).dividerColor),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 6, 8, 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: TextField(
                controller: input,
                minLines: 1,
                maxLines: 4,
                decoration: const InputDecoration(
                  hintText: 'Ask Maeve anything',
                  border: InputBorder.none,
                ),
              ),
            ),
            IconButton(
              tooltip: 'Send',
              onPressed: _send,
              icon: const Icon(Icons.arrow_upward_rounded),
            ),
          ],
        ),
      ),
    ),
  );

  void _send() {
    final text = input.text.trim();
    if (text.isEmpty) return;
    setState(() {
      messages.add(text);
      messages.add('I have it. The next move is yours.');
      input.clear();
    });
  }
}

TextStyle _eyebrow(BuildContext context) => TextStyle(
  fontSize: 10,
  letterSpacing: 1.5,
  fontWeight: FontWeight.w700,
  color: Theme.of(context).colorScheme.onSurfaceVariant,
);

enum MarkState { idle, armed }

class MaeveMark extends StatelessWidget {
  const MaeveMark({super.key, required this.size, this.state = MarkState.idle});
  final double size;
  final MarkState state;
  @override
  Widget build(BuildContext context) {
    final surface = Theme.of(context).colorScheme.surface;
    return CustomPaint(
      size: Size.square(size),
      painter: _MarkPainter(Theme.of(context).colorScheme.onSurface, surface),
    );
  }
}

class _MarkPainter extends CustomPainter {
  const _MarkPainter(this.color, this.background);
  final Color color;
  final Color background;
  @override
  void paint(Canvas canvas, Size size) {
    final c = size.center(Offset.zero);
    final r = size.shortestSide * .3;
    final p = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.shortestSide * .045
      ..strokeCap = StrokeCap.round;
    canvas.drawCircle(c, r, p);
    canvas.drawCircle(
      c,
      r * .67,
      Paint()
        ..color = color
        ..style = PaintingStyle.fill,
    );
    canvas.drawCircle(c, r * .4, Paint()..color = background);
  }

  @override
  bool shouldRepaint(covariant _MarkPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.background != background;
}

class MaeveBridge {
  static const methods = MethodChannel('maeve/ui');
  static const events = EventChannel('maeve/ui/events');
  static Future<void> openSettings() => methods.invokeMethod('open_settings');
  static Stream<dynamic> get status => events.receiveBroadcastStream();
}
