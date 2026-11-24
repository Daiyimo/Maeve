import '../bridge/maeve_settings_bridge.dart';

import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';

import '../design/maeve_accent.dart';
import '../design/maeve_surfaces.dart';

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
  Future<void>? _capabilityLoading;
  Set<String>? _features;
  bool reconnecting = false;
  Future<void> _loadCapabilities() async {
    try {
      _features = await _bridge.supportedFeatures();
    } catch (_) {
      chatError = '无法读取宿主能力，请重新连接后重试';
      rethrow;
    }
  }

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
    settingsError = switch (next.providerState) {
      'corrupt' => '模型配置或凭据损坏，原文件已保留，暂时禁止写入。',
      'unavailable' => '模型配置暂时不可读，已禁止写入，请恢复存储后重试。',
      _ => null,
    };
    if (appearance) {
      mode = switch (next.mode) {
        'light' => ThemeMode.light,
        'system' => ThemeMode.system,
        'dark' => ThemeMode.dark,
        _ => ThemeMode.light,
      };
      accentPreset = MaeveAccentPreset.byId(next.accent);
      haptics = next.haptics;
      MaeveHaptics.enabled = haptics;
      glassDensity = MaeveGlassDensity.byId(next.glassStyle);
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
  Future<void> recoverProviders() async => _applySettings(
    await settingsBridge.recoverProviders(),
    appearance: false,
  );
  Future<void> deleteModel(String id, {bool wholeProvider = false}) async =>
      _applySettings(
        await settingsBridge.deleteModel(id, wholeProvider: wholeProvider),
        appearance: false,
      );
  Future<void> completeOnboarding() async => _applySettings(
    await settingsBridge.completeOnboarding(),
    appearance: false,
  );
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

  Future<void> setTaskStepLimit(int limit) async {
    final revision = ++_settingsRevision;
    final next = await settingsBridge.setTaskStepLimit(limit);
    if (revision == _settingsRevision) _applySettings(next, appearance: false);
  }

  Future<void> setCapsuleEnabled(bool enabled) async {
    final revision = ++_settingsRevision;
    final next = await settingsBridge.setCapsuleEnabled(enabled);
    if (revision == _settingsRevision) _applySettings(next, appearance: false);
  }

  // —— 外观 ——
  ThemeMode mode = ThemeMode.light;

  /// 当前配色预设(六套之一)。推导出来的完整色位见 [accent]。
  MaeveAccentPreset accentPreset = MaeveAccentPreset.reverie;

  /// 当前配色推导完的色位(页面取色都走主题,不直接用这个)。
  MaeveAccent get accent => deriveAccent(accentPreset);

  /// 液态玻璃浓度(设置页"外观与手感 → 液态玻璃")。
  MaeveGlassDensity glassDensity = MaeveGlassDensity.balanced;
  bool haptics = true;

  // —— 会话(桥接真实数据)——
  final List<Conversation> conversations = [];
  Set<String> _summaryIds = {};
  String activeId = '';
  int _draftGeneration = DateTime.now().microsecondsSinceEpoch;
  Future<void>? _draftLoading;
  Future<void> _draftWrites = Future.value();
  Timer? _draftTimer;
  bool _draftStorageFailed = false;
  int _draftStoreRevision = 0;
  bool _draftDirty = false;
  String? draftError;
  int draftRestoreRevision = 0;
  String? _resumeDraftKey;
  final _draftKeys = <String, String>{};
  String get draftKey => activeId.isEmpty
      ? 'new:$_draftGeneration'
      : _draftKeys[activeId] ?? activeId;
  final drafts =
      <String, ({String text, ({String text, bool fromUser})? quote})>{};

  void saveDraft(
    String key,
    String text,
    ({String text, bool fromUser})? quote,
  ) {
    if (_disposed || drafts[key] == (text: text, quote: quote)) return;
    if (text.isEmpty && quote == null) {
      if (drafts.remove(key) == null) return;
    } else {
      drafts[key] = (text: text, quote: quote);
    }
    _draftDirty = true;
    _draftTimer?.cancel();
    _draftTimer = Timer(const Duration(milliseconds: 250), () {
      unawaited(flushDrafts().catchError((_) {}));
    });
  }

  /// 提交前必须持久化原 requestId。普通输入合并写入，不把业务正文放进诊断。
  Future<void> flushDrafts() async {
    _draftTimer?.cancel();
    await (_capabilityLoading ??= _loadCapabilities());
    await (_draftLoading ??= _restoreDrafts());
    if (_draftStorageFailed) throw StateError('drafts_unavailable');
    final state = jsonEncode({
      'schemaVersion': 1,
      'generation': _draftGeneration,
      'activeKey': draftKey,
      'aliases': {
        for (final e in _draftKeys.entries)
          if (drafts.containsKey(e.value) ||
              _pendingSubmissions.containsKey(e.value) ||
              e.key == activeId)
            e.key: e.value,
      },
      'drafts': {
        for (final e in drafts.entries)
          e.key: {
            'text': e.value.text,
            'quote': e.value.quote == null
                ? null
                : {
                    'text': e.value.quote!.text,
                    'fromUser': e.value.quote!.fromUser,
                  },
          },
      },
      'pending': {
        for (final e in _pendingSubmissions.entries)
          e.key: {
            'id': e.value.id,
            'text': e.value.text,
            'quote': e.value.quote,
            'fromUser': e.value.fromUser,
          },
      },
    });
    final write = _draftWrites.catchError((_) {}).then((_) async {
      if (_draftStorageFailed) throw StateError('drafts_unavailable');
      _draftStoreRevision = await _bridge.saveDraftState(
        state,
        _draftStoreRevision,
      );
    });
    _draftWrites = write;
    try {
      await write;
      if (identical(_draftWrites, write)) _draftDirty = false;
      final hadError = draftError != null;
      draftError = null;
      if (hadError && !_disposed) notifyListeners();
    } catch (_) {
      _draftStorageFailed = true;
      draftError = '草稿尚未保存到设备，请检查存储后重试';
      if (!_disposed) notifyListeners();
      rethrow;
    }
  }

  Future<void> _restoreDrafts() async {
    try {
      if (_features != null && !_features!.contains('draftRecovery')) {
        _draftStorageFailed = true;
        draftError = '宿主不支持草稿恢复，请先升级应用；当前输入已保留';
        return;
      }
      final stored = await _bridge.loadDraftState();
      final raw = stored.state;
      _draftStoreRevision = stored.revision;
      if (raw == null) return;
      final state = jsonDecode(raw) as Map;
      if (state['schemaVersion'] != 1) throw StateError('drafts_schema');
      final untouched = drafts.isEmpty && _navigationGeneration == 0;
      final savedDrafts = (state['drafts'] as Map).map((key, row) {
        final q = row['quote'];
        return MapEntry(key as String, (
          text: row['text'] as String,
          quote: q == null
              ? null
              : (text: q['text'] as String, fromUser: q['fromUser'] == true),
        ));
      });
      final savedPending = (state['pending'] as Map).map(
        (key, row) => MapEntry(key as String, (
          id: row['id'] as String,
          text: row['text'] as String,
          quote: row['quote'] as String?,
          fromUser: row['fromUser'] == true,
        )),
      );
      final aliases = Map<String, String>.from(state['aliases'] as Map);
      final generation = (state['generation'] as num).toInt();
      final activeKey = state['activeKey'] as String;
      // 先完整解析再发布；读取期间的新输入不能被旧草稿覆盖。
      for (final e in savedDrafts.entries) {
        drafts.putIfAbsent(e.key, () => e.value);
      }
      _pendingSubmissions.addAll(savedPending);
      for (final e in aliases.entries) {
        _draftKeys.putIfAbsent(e.key, () => e.value);
      }
      if (untouched) _draftGeneration = generation;
      if (untouched) _resumeDraftKey = activeKey;
      draftRestoreRevision++;
      if (!_disposed) notifyListeners();
    } catch (_) {
      _draftStorageFailed = true;
      draftError = '无法恢复本地草稿，原文件已保留。请勿重复发送未确认的任务';
      if (!_disposed) notifyListeners();
    }
  }

  String search = '';
  Future<void> retryDraftStorage() async {
    await _draftWrites.catchError((_) {});
    _draftStorageFailed = false;
    _draftLoading = _restoreDrafts();
    try {
      await flushDrafts();
    } catch (_) {
      /* 保留界面上的修复提示。 */
    }
  }

  /// 会话抽屉的筛选:全部 / 置顶。
  int listFilter = 0;

  /// 宿主当前对话(ui.conversationId);壳里没有对话时为 null。
  String? hostConversationId;
  String? _requestedConversation;
  bool _acceptNewConversation = false;
  int _summaryGeneration = 0;
  final _eventRevisions = <String, int>{};
  String? _chatEpoch;
  final _retiredEpochs = <String>{};
  bool get supportsEffectReceipts =>
      _features?.contains('actionEffectReceipts') ?? false;
  final _pendingSubmissions =
      <String, ({String id, String text, String? quote, bool fromUser})>{};
  final _sending = <String>{};
  final _deletedIds = <String>{};
  final _deletingIds = <String>{};
  final _pinningIds = <String>{};
  final _cancelRequested = <String>{};
  bool get cancelRequested => _cancelRequested.contains(activeId);
  int _navigationGeneration = 0;
  Future<void> _navigation = Future.value();
  String? chatError;

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
    _capabilityLoading ??= _loadCapabilities();
    _draftLoading ??= _capabilityLoading!.then((_) => _restoreDrafts());
    if (_chatSub != null) return;
    _chatSub = _bridge.chatStream.listen(
      _onChatEvent,
      onError: (_) {
        if (!_disposed) {
          connected = false;
          chatError = '对话连接已中断，当前输入已保留，请重新连接';
          notifyListeners();
        }
      },
    );
    unawaited(_bootstrap());
  }

  Future<void> reconnect() async {
    if (_disposed || reconnecting) return;
    reconnecting = true;
    notifyListeners();
    try {
      await _chatSub?.cancel();
      _chatSub = null;
      _capabilityLoading = null;
      _draftLoading = null;
      _draftStorageFailed = false;
      chatError = null;
      attach();
      await _draftLoading;
    } catch (_) {
      chatError = '仍无法连接宿主，请稍后重试';
    } finally {
      reconnecting = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<void> _bootstrap() async {
    try {
      await _draftLoading;
      final generation = _summaryGeneration;
      final navigation = _navigationGeneration;
      final items = await _bridge.conversations();
      if (_disposed) return;
      connected = true;
      if (generation == _summaryGeneration) _applyConversations(items);
      if (navigation != _navigationGeneration) return;
      final resume = _resumeDraftKey;
      _resumeDraftKey = null;
      if (resume != null &&
          (drafts.containsKey(resume) ||
              _pendingSubmissions.containsKey(resume))) {
        final id = _draftKeys.entries
            .where((e) => e.value == resume && _summaryIds.contains(e.key))
            .firstOrNull
            ?.key;
        if (id != null || _summaryIds.contains(resume)) {
          activeId = id ?? resume;
          _requestedConversation = activeId;
          await _bridge.openConversation(activeId);
        } else if (resume.startsWith('new:')) {
          activeId = '';
          _requestedConversation = '';
          final created = await _bridge.createConversation();
          if (_disposed || navigation != _navigationGeneration) return;
          if (created != null && created.isNotEmpty) {
            _draftKeys[created] = resume;
            _requestedConversation = created;
            activeId = created;
          }
        }
        notifyListeners();
        return;
      }
      final current = hostConversationId;
      final desired = _requestedConversation;
      if (desired != null &&
          desired.isNotEmpty &&
          !_deletedIds.contains(desired)) {
        activeId = desired;
        await _bridge.openConversation(desired);
      } else if (desired == '') {
        activeId = '';
      } else if (current != null && conversations.any((c) => c.id == current)) {
        activeId = current;
      } else if (conversations.isNotEmpty) {
        activeId = conversations.first.id;
        await _bridge.openConversation(activeId);
      }
      notifyListeners();
    } catch (_) {
      if (_disposed) return;
      connected = false;
      chatError ??= '对话暂时不可读，当前输入已保留，请重新连接';
      // 桥不在(理论上同进程不可能):保持空态,不假装有数据
      notifyListeners();
    }
  }

  void _onChatEvent(dynamic event) {
    if (event is! Map || _disposed) return;
    final epoch = event['epoch'] as String?;
    if (epoch != null && epoch != _chatEpoch) {
      if (_retiredEpochs.contains(epoch)) return;
      if (_chatEpoch != null) _retiredEpochs.add(_chatEpoch!);
      _chatEpoch = epoch;
      _eventRevisions.clear();
      _summaryGeneration++;
    }
    final revision = (event['revision'] as num?)?.toInt();
    final key = '${event['kind']}:${event['conversationId'] ?? ''}';
    if (revision != null) {
      if (revision <= (_eventRevisions[key] ?? -1)) return;
      _eventRevisions[key] = revision;
    }
    switch (event['kind']) {
      case 'conversations':
        _summaryGeneration++;
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
        .where((e) => !_deletedIds.contains(e['id']))
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
    final incoming = event['conversationId'] as String? ?? '';
    if (_deletedIds.contains(incoming) || _deletingIds.contains(incoming)) {
      return;
    }
    if (activeId.isEmpty && _acceptNewConversation && incoming.isNotEmpty) {
      _draftKeys[incoming] = draftKey;
    }
    if (_requestedConversation != null && incoming != _requestedConversation) {
      if (!(_requestedConversation == '' &&
          _acceptNewConversation &&
          incoming.isNotEmpty)) {
        return;
      }
      _draftKeys[incoming] = draftKey;
      _requestedConversation = incoming;
      _acceptNewConversation = false;
    }
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
      final first = (event['exchanges'] as List? ?? [])
          .whereType<Map>()
          .firstOrNull;
      final words = _splitQuote(first?['goal'] as String? ?? '').words
          .replaceAll(RegExp(r'\s+'), ' ')
          .trim();
      final title = String.fromCharCodes(words.runes.take(18));
      if (conversations.every((c) => c.id != conversationId)) {
        conversations.insert(
          0,
          Conversation(
            id: conversationId,
            title: title.isEmpty ? '新对话' : title,
          ),
        );
      } else if (!_summaryIds.contains(conversationId) &&
          _find(conversationId).title == '新对话' &&
          title.isNotEmpty) {
        _find(conversationId).title = title;
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
    if (!conversation.messages.any((m) => !m.isUser && m.active)) {
      _cancelRequested.remove(conversationId);
    }
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
    final plan = exchange['plan'] is Map
        ? TaskPlanView.fromMap(exchange['plan'] as Map)
        : null;
    if (text.isEmpty && line.isEmpty && steps == 0 && status == 'done') {
      return;
    }
    yield Message(
      text.isNotEmpty ? text : line,
      isUser: false,
      steps: steps,
      elapsedMs: (exchange['elapsedMs'] as num?)?.toInt(),
      failed: status == 'failed',
      status: status,
      runId: exchange['runId'] as String?,
      executionSteps: executionSteps,
      detail: exchange['detail'] as String?,
      confirmationKey: (exchange['confirmationKey'] as num?)?.toInt(),
      resumable: exchange['resumable'] == true,
      waitingForModel: exchange['waitingForModel'] == true,
      planTitle: plan?.title,
      planCurrentStep: plan?.currentStep,
      planSteps: plan?.steps ?? const [],
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
    _draftTimer?.cancel();
    if (_draftDirty) unawaited(flushDrafts().catchError((_) {}));
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
    _navigationGeneration++;
    _requestedConversation = threadId;
    if (conversations.every((conversation) => conversation.id != threadId)) {
      conversations.insert(0, Conversation(id: threadId, title: threadId));
    }
    activeId = threadId;
    notifyListeners();
    _navigation = _bridge.openConversation(threadId);
    unawaited(_navigation.catchError((_) => _navigationFailed()));
  }

  /// 抽屉列表:置顶 → 按天分组,搜索与筛选在这里生效。
  List<Conversation> get visibleConversations {
    final query = search.trim().toLowerCase();
    var items = conversations.where((conversation) {
      if (listFilter == 1 && !conversation.pinned) return false;
      if (query.isEmpty) return true;
      return conversation.title.toLowerCase().contains(query);
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
    if (chatError != null) return chatError!;
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

  /// 换液态玻璃的浓度:当场换,不重建界面(表面由 MaeveThemeScope 现算)。
  void setGlassDensity(MaeveGlassDensity next) {
    if (glassDensity == next) return;
    glassDensity = next;
    unawaited(_saveAppearance({'glassStyle': next.id}));
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
    _navigationGeneration++;
    chatError = null;
    activeId = id;
    notifyListeners();
    _requestedConversation = id;
    _navigation = _bridge.openConversation(id);
    unawaited(_navigation.catchError((_) => _navigationFailed()));
  }

  /// 新对话:宿主切到新状态(还没说话就没有 id),壳进入空态;第一句话发出去时
  /// 宿主才建档,conversations 事件随之带来真实列表。
  void create() {
    final generation = ++_navigationGeneration;
    chatError = null;
    _draftGeneration++;
    final key = 'new:$_draftGeneration';
    _requestedConversation = '';
    _acceptNewConversation = false;
    activeId = '';
    generating = false;
    _navigation = _bridge.createConversation().then((id) {
      if (_disposed || generation != _navigationGeneration) return;
      if (id != null && id.isNotEmpty) {
        _draftKeys[id] = key;
        _requestedConversation = id;
        hostConversationId = id;
        activeId = id;
      }
      notifyListeners();
    });
    unawaited(_navigation.catchError((_) => _navigationFailed()));
    notifyListeners();
  }

  void _navigationFailed() {
    if (_disposed) return;
    chatError = '切换对话失败，请重新选择';
    notifyListeners();
  }

  void togglePin(String id) {
    if (!_pinningIds.add(id)) return;
    final conversation = _find(id);
    final pinned = !conversation.pinned;
    unawaited(_savePin(id, pinned));
  }

  Future<void> _savePin(String id, bool pinned) async {
    try {
      await _bridge.setPinned(id, pinned);
      if (_disposed || _deletedIds.contains(id)) return;
      _find(id).pinned = pinned;
      notifyListeners();
    } catch (_) {
      if (!_disposed) {
        chatError = '置顶状态未能保存，请重试';
        notifyListeners();
      }
    } finally {
      _pinningIds.remove(id);
    }
  }

  Future<void> delete(String id) async {
    await (_capabilityLoading ??= _loadCapabilities());
    if (!(_features?.contains('awaitDeletion') ?? false)) {
      throw UnsupportedError('awaitDeletion');
    }
    if (!_deletingIds.add(id)) throw StateError('delete_pending');
    try {
      await _bridge.delete(id);
      _deletedIds.add(id);
    } finally {
      _deletingIds.remove(id);
    }
    if (_disposed) return;
    drafts.remove(_draftKeys[id] ?? id);
    _pendingSubmissions.remove(_draftKeys[id] ?? id);
    _draftKeys.remove(id);
    // 原生完整删除也清理了私有草稿，因此在提交剩余草稿前读取新的存储代次。
    await _draftWrites.catchError((_) {});
    _draftStoreRevision = (await _bridge.loadDraftState()).revision;
    _draftStorageFailed = false;
    await flushDrafts();
    conversations.removeWhere((conversation) => conversation.id == id);
    if (activeId == id) {
      activeId = conversations.isNotEmpty ? conversations.first.id : '';
      _requestedConversation = activeId;
      _navigationGeneration++;
      if (activeId.isNotEmpty) await _bridge.openConversation(activeId);
    }
    notifyListeners();
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
  Future<bool> send(
    String raw, {
    String? quote,
    bool quoteFromUser = false,
  }) async {
    final text = raw.trim();
    if (text.isEmpty) return false;
    final key = draftKey;
    final navigation = _navigationGeneration;
    if (!_sending.add(key)) return false;
    try {
      await (_capabilityLoading ??= _loadCapabilities());
      if (!(_features?.contains('submissionReceipts') ?? false) ||
          !(_features?.contains('draftRecovery') ?? false)) {
        chatError = '宿主不支持可靠发送，请先升级应用；当前输入已保留';
        if (!_disposed) notifyListeners();
        return false;
      }
      await (_draftLoading ??= _restoreDrafts());
      if (_draftStorageFailed) throw StateError('drafts_unavailable');
      await _navigation;
      if (_disposed || navigation != _navigationGeneration) return false;
      chatError = null;
      final pending = _pendingSubmissions[key];
      if (pending != null) {
        final accepted = await _bridge.submissionReceipt(pending.id);
        if (accepted == null) throw StateError('receipt_unknown');
        _settleDraft(key, accepted);
        await flushDrafts();
        if (pending.text.trim() == text &&
            pending.quote == quote &&
            pending.fromUser == quoteFromUser) {
          return accepted;
        }
      }
      final id =
          'ui_${DateTime.now().microsecondsSinceEpoch}_${Random.secure().nextInt(1 << 32)}';
      _pendingSubmissions[key] = (
        id: id,
        text: raw,
        quote: quote,
        fromUser: quoteFromUser,
      );
      await flushDrafts();
      _acceptNewConversation = activeId.isEmpty;
      bool? accepted;
      try {
        accepted = await _bridge.submit(
          id,
          activeId,
          text,
          quote: quote,
          quoteFromUser: quoteFromUser,
        );
      } catch (_) {
        accepted = await _bridge.submissionReceipt(id);
      }
      if (accepted == null) throw StateError('receipt_unknown');
      _settleDraft(key, accepted);
      await flushDrafts();
      return accepted;
    } finally {
      _sending.remove(key);
    }
  }

  void _settleDraft(String key, bool accepted) {
    final sent = _pendingSubmissions.remove(key);
    final current = drafts[key];
    if (accepted &&
        sent != null &&
        current?.text == sent.text &&
        current?.quote?.text == sent.quote &&
        (current?.quote?.fromUser ?? false) == sent.fromUser) {
      drafts.remove(key);
    }
  }

  /// 重发(失败轮次的重试):当新的一句话发给宿主。
  void sendQueued(Message message) {
    unawaited(send(message.text).catchError((_) => false));
  }

  /// 停止当前任务:宿主 TaskService.cancel。
  void stop() {
    unawaited(requestStop());
  }

  Future<void> requestStop() async {
    final id = activeId;
    if (id.isEmpty || !_cancelRequested.add(id)) return;
    notifyListeners();
    try {
      await _bridge.cancelConversation(id);
      // 接收取消请求不代表任务已经停止；只有宿主终态能解除等待。
    } catch (_) {
      _cancelRequested.remove(id);
      chatError = '停止请求未能确认，请重试';
      if (!_disposed) notifyListeners();
    }
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
