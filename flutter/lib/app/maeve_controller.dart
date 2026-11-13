import '../bridge/maeve_settings_bridge.dart';

import 'dart:async';

import 'package:flutter/material.dart';

import '../design/maeve_accent.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../models/conversation.dart';
import '../models/execution.dart';
import '../ui/common/haptics.dart';

/// 全局状态:持久化外观、原生模型配置、真实对话与运行态。
///
/// 会话列表与轮次是**真实数据**:来自宿主的 ConversationStore / ChatViewModel
/// (桥接面见 MaeveChatBridge / 宿主 MaeveChatPlugin)。壳这边只留本地 Concerns:
/// 仅保留真实会话与配置、搜索和筛选。发送/停止/置顶/删除
/// 都转发给宿主,不本地伪造。接桥前的演示态(_seed/_demoReply)已移除。
class MaeveController extends ChangeNotifier {
  MaeveController({
    MaeveChatBridge? bridge,
    MaeveSettingsBridge? settingsBridge,
  }) : settingsBridge = settingsBridge ?? const HostSettingsBridge(),
       _bridge = bridge ?? const HostChatBridge() {
    MaeveHaptics.enabled = haptics;
  }

  /// 对话桥:真机走平台通道,测试换假实现(见 MaeveChatBridge)。
  final MaeveChatBridge _bridge;
  MaeveChatBridge get chatBridge => _bridge;
  final MaeveSettingsBridge settingsBridge;
  NativeSettings native = const NativeSettings();
  String? settingsError;
  bool settingsLoaded = false;
  bool _disposed = false;
  int _settingsRevision = 0;

  Future<void> refreshSettings() async {
    final revision = ++_settingsRevision;
    try {
      final next = await settingsBridge.load();
      if (revision == _settingsRevision) _applySettings(next);
    } catch (_) {
      if (!_disposed) {
        settingsError = '暂时无法读取设备设置，请重试';
        notifyListeners();
      }
    }
  }

  void _applySettings(NativeSettings next, {bool appearance = true}) {
    if (_disposed) return;
    native = next;
    settingsLoaded = true;
    settingsError = null;
    if (appearance) {
      mode = switch (next.mode) {
        'light' => ThemeMode.light,
        'system' => ThemeMode.system,
        _ => ThemeMode.dark,
      };
      accentPreset = MaeveAccentPreset.byId(next.accent);
      haptics = next.haptics;
      MaeveHaptics.enabled = haptics;
      reduceTransparency = next.reduceTransparency;
    }
    model = next.modelName ?? '选择模型';
    notifyListeners();
  }

  Future<void> _saveAppearance(Map<String, Object> values) async {
    _settingsRevision++;
    try {
      await settingsBridge.appearance(values);
    } catch (_) {
      if (!_disposed) {
        settingsError = '外观设置未能保存，请重试';
        notifyListeners();
      }
    }
  }

  Future<void> selectModel(String id) async =>
      _applySettings(await settingsBridge.selectModel(id), appearance: false);
  Future<void> saveModel(Map<String, String> values) async =>
      _applySettings(await settingsBridge.saveModel(values), appearance: false);
  Future<void> setAutonomous(bool enabled) async {
    final revision = ++_settingsRevision;
    final next = await settingsBridge.setAutonomous(enabled);
    if (revision == _settingsRevision) _applySettings(next, appearance: false);
  }

  Future<void> setOperatingPace(String pace) async {
    final revision = ++_settingsRevision;
    final next = await settingsBridge.setOperatingPace(pace);
    if (revision == _settingsRevision) _applySettings(next, appearance: false);
  }

  Future<void> setCapsuleEnabled(bool enabled) async {
    final revision = ++_settingsRevision;
    final next = await settingsBridge.setCapsuleEnabled(enabled);
    if (revision == _settingsRevision) _applySettings(next, appearance: false);
  }

  // —— 外观 ——
  ThemeMode mode = ThemeMode.dark;

  /// 当前配色预设(六套之一)。推导出来的完整色位见 [accent]。
  MaeveAccentPreset accentPreset = MaeveAccentPreset.reverie;

  /// 当前配色推导完的色位(页面取色都走主题,不直接用这个)。
  MaeveAccent get accent => deriveAccent(accentPreset);
  bool reduceTransparency = false;
  bool haptics = true;

  // —— 会话(桥接真实数据)——
  final List<Conversation> conversations = [];
  Set<String> _summaryIds = {};
  String activeId = '';
  String search = '';

  /// 会话抽屉的筛选:全部 / 置顶。
  int listFilter = 0;

  /// 宿主当前对话(ui.conversationId);壳里没有对话时为 null。
  String? hostConversationId;

  /// 发送被前置条件拦下时的问题码(no_model / accessibility / …);为空表示没被拦。
  List<String> blocked = const [];

  StreamSubscription? _chatSub;

  bool generating = false;
  bool connected = false;
  String model = '选择模型';

  /// 接上对话桥:拉会话列表、订事件流、打开当前(或第一个)对话。
  /// 在 MaeveApp.initState 里调一次。
  void attach() {
    unawaited(refreshSettings());
    if (_chatSub != null) return;
    _chatSub = _bridge.chatStream.listen(_onChatEvent);
    unawaited(_bootstrap());
  }

  Future<void> _bootstrap() async {
    try {
      final items = await _bridge.conversations();
      if (_disposed) return;
      connected = true;
      _applyConversations(items);
      final current = hostConversationId;
      if (current != null && conversations.any((c) => c.id == current)) {
        activeId = current;
      } else if (conversations.isNotEmpty) {
        activeId = conversations.first.id;
        await _bridge.openConversation(activeId);
      }
      notifyListeners();
    } catch (_) {
      if (_disposed) return;
      connected = false;
      // 桥不在(理论上同进程不可能):保持空态,不假装有数据
      notifyListeners();
    }
  }

  void _onChatEvent(dynamic event) {
    if (event is! Map) return;
    switch (event['kind']) {
      case 'conversations':
        _applyConversations(event['items']);
        notifyListeners();
      case 'ui':
        _applyUi(event);
        notifyListeners();
    }
  }

  void _applyConversations(dynamic raw) {
    final items = (raw as List<dynamic>? ?? [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
    final previous = {
      for (final conversation in conversations) conversation.id: conversation,
    };
    _summaryIds = items.map((item) => item['id'] as String).toSet();
    final next = items.map((item) {
      final id = item['id'] as String;
      final conversation = previous[id] ?? Conversation(id: id, title: '');
      conversation
        ..title = (item['title'] as String?) ?? ''
        ..updatedAt = DateTime.fromMillisecondsSinceEpoch(
          (item['updatedAt'] as num).toInt(),
        )
        ..pinned = item['pinned'] == true;
      return conversation;
    }).toList();
    // The UI snapshot can precede first persistence/title generation. A summary
    // is metadata, not a replacement for the attached conversation's turns.
    final attached = previous[hostConversationId];
    if (attached != null &&
        attached.id == activeId &&
        !_summaryIds.contains(attached.id) &&
        attached.messages.isNotEmpty) {
      next.insert(0, attached);
    }
    conversations
      ..clear()
      ..addAll(next);
    if (activeId.isNotEmpty && conversations.every((c) => c.id != activeId)) {
      // 当前对话被删了:落到还剩的第一个
      activeId = conversations.isNotEmpty ? conversations.first.id : '';
    }
  }

  /// 宿主轮次快照 → 壳的消息列表。一问一答 = 用户气泡 + 助手轮次。
  void _applyUi(Map event) {
    final previousHost = hostConversationId;
    hostConversationId = event['conversationId'] as String?;
    final conversationId = hostConversationId;
    if (previousHost != conversationId && !_summaryIds.contains(previousHost)) {
      conversations.removeWhere(
        (conversation) => conversation.id == previousHost,
      );
    }
    activeId = conversationId ?? '';
    if (conversationId != null && conversationId.isNotEmpty) {
      if (conversations.every((c) => c.id != conversationId)) {
        // 新对话(第一次发送才在 store 里建档):先放个占位,标题等 conversations 事件
        conversations.insert(0, Conversation(id: conversationId, title: '新对话'));
      }
      activeId = conversationId;
    }
    blocked = ((event['blocked'] as List<dynamic>?) ?? const [])
        .map((e) => e.toString())
        .toList(growable: false);
    final exchanges = event['exchanges'] as List<dynamic>? ?? const [];
    // Native live means the attached turn, including its finished result.
    // ComposerMode is the execution authority; terminal turns must stop work UI.
    generating = event['composer'] == 'busy';

    final conversation = _find(activeId);
    conversation.messages
      ..clear()
      ..addAll(exchanges.whereType<Map>().expand(_messagesOfExchange));
  }

  Iterable<Message> _messagesOfExchange(Map exchange) sync* {
    final goal = exchange['goal'] as String? ?? '';
    final quoted = _splitQuote(goal);
    yield Message(
      quoted.words,
      isUser: true,
      quote: quoted.quote,
      quoteFromUser: quoted.fromUser,
    );
    final status = exchange['status'] as String? ?? '';
    final line = exchange['line'] as String? ?? '';
    final text =
        [exchange['answer'], exchange['detail'], line]
            .whereType<String>()
            .where((value) => value.trim().isNotEmpty)
            .firstOrNull ??
        (status == 'failed' ? '任务失败，请重试或检查模型配置。' : '');
    final steps = (exchange['steps'] as num?)?.toInt() ?? 0;
    final executionSteps = [
      for (final row in exchange['executionSteps'] as List? ?? const [])
        if (row is Map) ExecutionStep.fromMap(row),
    ];
    if (text.isEmpty &&
        line.isEmpty &&
        steps == 0 &&
        const ['done', 'cancelled'].contains(status)) {
      return;
    }
    yield Message(
      text.isNotEmpty ? text : line,
      isUser: false,
      steps: steps,
      failed: status == 'failed',
      status: status,
      runId: exchange['runId'] as String?,
      executionSteps: executionSteps,
      detail: exchange['detail'] as String?,
      confirmationKey: (exchange['confirmationKey'] as num?)?.toInt(),
      resumable: exchange['resumable'] == true,
      waitingForModel: exchange['waitingForModel'] == true,
    );
  }

  /// 宿主持久化的用户话可能带着引用块(QuoteMessage 格式:首行 header,随后每行
  /// 「> 」是引的内容,空行后是这次说的话)。拆回 引用 + 正文,和本地发出时一致。
  /// 格式的定义在宿主 core/agent/QuoteMessage.kt,这边只做渲染用的解析。
  ({String? quote, String words, bool fromUser}) _splitQuote(String goal) {
    const headerMaeve = '> [Quoting your earlier reply]';
    const headerYou = '> [Quoting my earlier message]';
    final lines = goal.split('\n');
    final header = lines.isEmpty ? '' : lines.first.trim();
    final fromUser = header == headerYou;
    if (header != headerMaeve && !fromUser) {
      return (quote: null, words: goal, fromUser: false);
    }
    final quoted = <String>[];
    var i = 1;
    while (i < lines.length && lines[i].startsWith('>')) {
      var line = lines[i].substring(1);
      if (line.startsWith(' ')) line = line.substring(1);
      quoted.add(line);
      i++;
    }
    return (
      quote: quoted.join('\n').trim(),
      words: lines.skip(i).join('\n').trim(),
      fromUser: fromUser,
    );
  }

  @override
  void dispose() {
    _disposed = true;
    _chatSub?.cancel();
    super.dispose();
  }

  Conversation get active => _find(activeId);

  /// 入口事件:打开某个对话(通知点击、任务深链接带进来的 threadId)。
  ///
  /// 命中真实会话就切过去;miss 时(壳还没同步到这条)放一个以 threadId 为标题的
  /// 占位会话——它是"这个入口接到了"的可见证据,不是真实内容。宿主那边的
  /// openConversation 会把真实轮次带回来(对话不存在时是空的)。
  void openThread(String threadId) {
    if (conversations.every((conversation) => conversation.id != threadId)) {
      conversations.insert(0, Conversation(id: threadId, title: threadId));
    }
    activeId = threadId;
    notifyListeners();
    unawaited(_bridge.openConversation(threadId));
  }

  /// 抽屉列表:置顶 → 按天分组,搜索与筛选在这里生效。
  List<Conversation> get visibleConversations {
    final query = search.trim().toLowerCase();
    var items = conversations.where((conversation) {
      if (listFilter == 1 && !conversation.pinned) return false;
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

  /// 顶栏标题上方那行等宽标签(机器声部):一眼看出她此刻在什么状态。
  String get eyebrow {
    if (!connected) return 'HOST · OFFLINE';
    if (generating) return 'HOST · RUNNING';
    return 'HOST · READY';
  }

  /// 状态行的一句人话(B3:不走秒)。
  String get statusLine {
    if (!connected) return '本机 · 未连接';
    if (generating) return '正在思考';
    if (settingsLoaded && native.currentModelId == null) return '等待模型配置';
    return '本机 · 就绪';
  }

  /// 发送被拦下时给人看的一句话。问题码来自宿主的 Prerequisites.blockers
  /// (maeve/chat 的 send 返回 false 后,ui 事件把码带回来);空 = 没被拦。
  String? get blockedNotice {
    if (blocked.isEmpty) return null;
    final lines = blocked.map((code) => _blockedText[code] ?? code);
    return '发不出去：${lines.join('；')}。';
  }

  static const _blockedText = {
    'no_model': '还没配模型，去设置里加一个',
    'accessibility': '无障碍服务没开',
    'phone_helper': '手机助手没就绪',
    'system_too_old': '系统版本太低（手机操作需要 Android 12 以上）',
    'notifications_off': '通知被关了',
    'attention_lowered': '提醒频道被调低了',
    'focus_may_leak': '后台操作可能让主屏输入焦点短暂丢失',
  };

  // —— 外观动作 ——
  void setMode(ThemeMode next) {
    mode = next;
    unawaited(_saveAppearance({'mode': next.name}));
    notifyListeners();
  }

  void setAccent(MaeveAccentPreset next) {
    accentPreset = next;
    unawaited(_saveAppearance({'accent': next.id}));
    notifyListeners();
  }

  void toggleReduceTransparency() {
    reduceTransparency = !reduceTransparency;
    unawaited(_saveAppearance({'reduceTransparency': reduceTransparency}));
    notifyListeners();
  }

  void toggleHaptics() {
    haptics = !haptics;
    // 触感只有一个入口:这里关掉,整棵树都静音。
    MaeveHaptics.enabled = haptics;
    unawaited(_saveAppearance({'haptics': haptics}));
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
    activeId = id;
    notifyListeners();
    unawaited(_bridge.openConversation(id));
  }

  /// 新对话:宿主切到新状态(还没说话就没有 id),壳进入空态;第一句话发出去时
  /// 宿主才建档,conversations 事件随之带来真实列表。
  void create() {
    _bridge.newConversation();
    activeId = '';
    generating = false;
    notifyListeners();
  }

  void togglePin(String id) {
    final conversation = _find(id);
    final pinned = !conversation.pinned;
    conversation.pinned = pinned;
    notifyListeners();
    unawaited(_bridge.setPinned(id, pinned));
  }

  void delete(String id) {
    conversations.removeWhere((conversation) => conversation.id == id);
    if (activeId == id) {
      activeId = conversations.isNotEmpty ? conversations.first.id : '';
      if (activeId.isNotEmpty) {
        unawaited(_bridge.openConversation(activeId));
      }
    }
    notifyListeners();
    unawaited(_bridge.delete(id));
  }

  Conversation _find(String id) => conversations.firstWhere(
    (conversation) => conversation.id == id,
    orElse: () => _empty,
  );

  /// 列表空(新装机、还没说过话)时的临时对话:不进列表,只为让界面有东西可绑。
  Conversation get _empty => Conversation(id: '', title: 'Maeve');

  /// 发送:转发给宿主(建档、落盘、跑任务都归它)。界面先不动——用户这句话会随
  /// 下一个 ui 事件回来(宿主把它加进 exchanges)。被拦下时(缺模型、无障碍没开…)
  /// 宿主把问题码放在 ui.blocked,输入区据此提示。
  void send(String raw, {String? quote, bool quoteFromUser = false}) {
    final text = raw.trim();
    if (text.isEmpty) return;
    unawaited(_bridge.send(text, quote: quote, quoteFromUser: quoteFromUser));
  }

  /// 重发(失败轮次的重试):当新的一句话发给宿主。
  void sendQueued(Message message) => send(message.text);

  /// 停止当前任务:宿主 TaskService.cancel。
  void stop() {
    _bridge.cancel();
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
