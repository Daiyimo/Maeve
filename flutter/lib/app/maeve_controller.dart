import 'dart:async';

import 'package:flutter/material.dart';

import '../design/maeve_accent.dart';

import '../models/conversation.dart';
import '../ui/common/haptics.dart';

/// 细分偏好:设置二级页里的开关,集中放一处,免得控制器字段膨胀。
class MaevePrefs {
  /// 回复长度:0 简洁 / 1 适中 / 2 详细。
  int replyLength = 0;

  /// 思考过程默认展开。
  bool expandThinking = false;

  /// 回答里给来源。
  bool citeSources = true;

  /// 语音输入 / 朗读回复。
  bool voiceInput = false;
  bool readAloud = false;

  /// 通知:完成 / 需要确认 / 每日摘要。
  bool notifyDone = true;
  bool notifyAsk = true;
  bool notifyDigest = false;

  /// 低端机模式:自动关模糊、缩短动效。
  bool lowEndMode = false;

  /// 图标风格:0 圆润 / 1 单色。
  int iconStyle = 0;

  /// 日志级别:0 安静 / 1 标准 / 2 详细。
  int logLevel = 1;
}

/// 全局状态:外观、对话、记忆、运行态。
///
/// 原型阶段全部在进程内存里(不落盘),接桥后由 Android 侧提供持久化与真实回复。
class MaeveController extends ChangeNotifier {
  MaeveController() {
    MaeveHaptics.enabled = haptics;
    _seed();
  }

  // —— 外观 ——
  ThemeMode mode = ThemeMode.dark;

  /// 当前配色预设(六套之一)。推导出来的完整色位见 [accent]。
  MaeveAccentPreset accentPreset = MaeveAccentPreset.reverie;

  /// 当前配色推导完的色位(页面取色都走主题,不直接用这个)。
  MaeveAccent get accent => deriveAccent(accentPreset);
  bool reduceTransparency = false;
  bool haptics = true;

  /// 设置页里"外观"的图标风格(圆角/圆环),只影响图标画法。
  bool monochromeIcons = false;

  /// 细分偏好。
  final MaevePrefs prefs = MaevePrefs();

  void updatePrefs(void Function(MaevePrefs prefs) change) {
    change(prefs);
    notifyListeners();
  }

  // —— 会话 ——
  final List<Conversation> conversations = [];
  final List<String> memories = [];
  String activeId = 'c1';
  String search = '';

  /// 会话抽屉的筛选:全部 / 未读 / 置顶。
  int listFilter = 0;

  bool generating = false;
  bool connected = true;
  String model = 'Maeve';
  String modelTier = '高';

  /// 本会话是否自动同意(默认关;开着时输入条上方一直有胶囊)。
  bool autoApprove = false;

  int _generation = 0;
  int _sequence = 3;

  void _seed() {
    conversations
      ..clear()
      ..addAll([
        Conversation(
          id: 'c1',
          title: '选择之道',
          pinned: true,
          updatedAt: DateTime.now().subtract(const Duration(minutes: 6)),
        ),
        Conversation(
          id: 'c2',
          title: '搭一个安静的工作台',
          folder: '工作',
          updatedAt: DateTime.now().subtract(const Duration(hours: 5)),
        ),
        Conversation(
          id: 'c3',
          title: '新的开始',
          unread: 2,
          updatedAt: DateTime.now().subtract(const Duration(days: 2)),
        ),
      ]);
  }

  Conversation get active => conversations.firstWhere(
    (conversation) => conversation.id == activeId,
    orElse: () => conversations.first,
  );

  /// 抽屉列表:置顶 → 文件夹 → 按天分组,搜索与筛选在这里生效。
  List<Conversation> get visibleConversations {
    final query = search.trim().toLowerCase();
    var items = conversations.where((conversation) {
      if (conversation.archived) return false;
      if (listFilter == 1 && conversation.unread == 0) return false;
      if (listFilter == 2 && !conversation.pinned) return false;
      if (query.isEmpty) return true;
      return conversation.title.toLowerCase().contains(query) ||
          conversation.messages.any(
            (message) => message.text.toLowerCase().contains(query),
          );
    }).toList();

    items.sort((a, b) {
      if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
      return b.updatedAt.compareTo(a.updatedAt);
    });
    return items;
  }

  List<Conversation> get archivedConversations =>
      conversations.where((conversation) => conversation.archived).toList();

  /// 顶栏标题上方那行等宽标签(机器声部):一眼看出她此刻在什么状态。
  String get eyebrow {
    if (!connected) return 'HOST · OFFLINE';
    if (generating) return 'HOST · RUNNING';
    if (autoApprove) return 'HOST · AUTO';
    return 'HOST · READY';
  }

  /// 状态行的一句人话(B3:不走秒)。
  String get statusLine {
    if (!connected) return '本机 · 未连接';
    if (generating) return '正在思考';
    if (autoApprove) return '就绪 · 自动同意';
    return '本机 · 就绪';
  }

  // —— 外观动作 ——
  void toggleMode() {
    mode = mode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
    notifyListeners();
  }

  void setMode(ThemeMode next) {
    mode = next;
    notifyListeners();
  }

  void setAccent(MaeveAccentPreset next) {
    accentPreset = next;
    notifyListeners();
  }

  void toggleReduceTransparency() {
    reduceTransparency = !reduceTransparency;
    notifyListeners();
  }

  void toggleHaptics() {
    haptics = !haptics;
    // 触感只有一个入口:这里关掉,整棵树都静音。
    MaeveHaptics.enabled = haptics;
    notifyListeners();
  }

  void toggleMonochromeIcons() {
    monochromeIcons = !monochromeIcons;
    notifyListeners();
  }

  void setModelTier(String tier) {
    modelTier = tier;
    notifyListeners();
  }

  void toggleAutoApprove() {
    autoApprove = !autoApprove;
    notifyListeners();
  }

  // —— 会话动作 ——
  void setSearch(String value) {
    search = value;
    notifyListeners();
  }

  void setFilter(int value) {
    listFilter = value;
    notifyListeners();
  }

  void select(String id) {
    if (activeId == id) return;
    _generation++;
    generating = false;
    activeId = id;
    conversations.firstWhere((conversation) => conversation.id == id).unread =
        0;
    notifyListeners();
  }

  void create({bool selectNew = true}) {
    _generation++;
    generating = false;
    final id = 'c${++_sequence}';
    conversations.insert(0, Conversation(id: id, title: '新对话'));
    if (selectNew) activeId = id;
    notifyListeners();
  }

  void togglePin(String id) {
    final conversation = _find(id);
    conversation.pinned = !conversation.pinned;
    notifyListeners();
  }

  void archive(String id) {
    final conversation = _find(id);
    conversation.archived = true;
    if (activeId == id) {
      final next = conversations.firstWhere(
        (item) => !item.archived && item.id != id,
        orElse: () => conversation,
      );
      activeId = next.id;
    }
    notifyListeners();
  }

  void restore(String id) {
    _find(id).archived = false;
    notifyListeners();
  }

  void delete(String id) {
    conversations.removeWhere((conversation) => conversation.id == id);
    if (conversations.isEmpty) create();
    if (activeId == id) activeId = conversations.first.id;
    notifyListeners();
  }

  Conversation _find(String id) => conversations.firstWhere(
    (conversation) => conversation.id == id,
    orElse: () => conversations.first,
  );

  /// 发送。生成中再发 = 插话排队,不打断当前这一轮。
  ///
  /// [quote] 是引用的上一条消息摘要,发出去后留在气泡里(不只是输入框里的装饰)。
  void send(String raw, {String? quote, bool quoteFromUser = false}) {
    final text = raw.trim();
    if (text.isEmpty || !connected) return;
    final conversation = active;
    if (generating) {
      conversation.messages.add(
        Message(
          text,
          isUser: true,
          queued: true,
          quote: quote,
          quoteFromUser: quoteFromUser,
        ),
      );
      notifyListeners();
      return;
    }
    final generation = ++_generation;
    conversation.messages.add(
      Message(text, isUser: true, quote: quote, quoteFromUser: quoteFromUser),
    );
    if (conversation.title == '新对话') {
      conversation.title = text.length > 34
          ? '${text.substring(0, 34)}…'
          : text;
    }
    conversation.updatedAt = DateTime.now();
    generating = true;
    notifyListeners();
    unawaited(_demoReply(generation, conversation.id));
  }

  /// 排队中的插话:立刻发出去(停止当前这一轮)。
  void sendQueued(Message message) {
    final conversation = active;
    conversation.messages.remove(message);
    generating = false;
    send(message.text);
  }

  Future<void> _demoReply(int generation, String conversationId) async {
    await Future<void>.delayed(const Duration(milliseconds: 700));
    if (generation != _generation || !generating) return;
    final index = conversations.indexWhere((item) => item.id == conversationId);
    if (index < 0) return;
    final conversation = conversations[index];
    conversation.messages.add(
      Message(
        '我收到了，接下来交给你。',
        isUser: false,
        steps: 3,
        thinking: const ['理解了你的目标', '整理了两条可行路径', '把选择权交回给你'],
      ),
    );
    conversation.updatedAt = DateTime.now();
    generating = false;
    notifyListeners();
  }

  void stop() {
    _generation++;
    generating = false;
    notifyListeners();
  }

  // —— 记忆 ——
  void addMemory(String value) {
    final text = value.trim();
    if (text.isEmpty) return;
    memories.insert(0, text);
    notifyListeners();
  }

  void removeMemory(String value) {
    memories.remove(value);
    notifyListeners();
  }
}

/// 把控制器交给整棵树。用 InheritedNotifier,不引入第三方状态库。
class MaeveScope extends InheritedNotifier<MaeveController> {
  const MaeveScope({
    super.key,
    required MaeveController controller,
    required super.child,
  }) : super(notifier: controller);

  static MaeveController of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<MaeveScope>();
    assert(scope?.notifier != null, 'MaeveScope 不在树上');
    return scope!.notifier!;
  }

  /// 只读一次,不订阅变化(用于回调里取状态)。
  static MaeveController read(BuildContext context) {
    final scope = context.getInheritedWidgetOfExactType<MaeveScope>();
    assert(scope?.notifier != null, 'MaeveScope 不在树上');
    return scope!.notifier!;
  }
}
