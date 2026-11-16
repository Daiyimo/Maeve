import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/bridge/maeve_update_bridge.dart';
import 'package:maeve_flutter/models/app_update.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/update_page.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

/// 可控的更新桥:记录调用、按脚本返回状态,失败时抛和真机一样的 PlatformException。
class FakeUpdateBridge extends MaeveUpdateBridge {
  FakeUpdateBridge({AppUpdate? initial}) : _state = initial ?? const AppUpdate();

  AppUpdate _state;
  final calls = <String>[];
  final _changes = StreamController<AppUpdate>.broadcast();

  /// 置上后,下一次动作抛这个错误(模拟校验拒绝、连不上)。
  PlatformException? failure;

  /// install 返回什么:installing / permission。
  String installResult = 'installing';

  void emit(AppUpdate next) {
    _state = next;
    _changes.add(next);
  }

  @override
  Stream<AppUpdate> get changes => _changes.stream;

  Future<AppUpdate> _act(String name, AppUpdate next) async {
    calls.add(name);
    final error = failure;
    if (error != null) {
      failure = null;
      throw error;
    }
    _state = next;
    return next;
  }

  @override
  Future<AppUpdate> state() => _act('state', _state);

  @override
  Future<AppUpdate> check() => _act('check', _state);

  @override
  Future<AppUpdate> download() => _act('download', _state);

  @override
  Future<AppUpdate> cancel() => _act('cancel', _state);

  @override
  Future<String> install() async {
    calls.add('install');
    final error = failure;
    if (error != null) {
      failure = null;
      throw error;
    }
    return installResult;
  }

  Future<void> dispose() => _changes.close();
}

AppUpdate _update({
  bool hasUpdate = true,
  String phase = 'idle',
  String status = 'done',
  int? percent,
  bool canDownload = true,
  bool canInstall = true,
  int rounds = 1,
}) => AppUpdate.fromMap({
  'available': true,
  'currentVersion': '0.1.0-dolores',
  'status': status,
  'manualRounds': rounds,
  'hasUpdate': hasUpdate,
  'canInstall': canInstall,
  'releasesUrl': 'https://github.com/Daiyimo/Maeve/releases',
  'latest': {
    'version': '0.2.0',
    'name': 'Maeve 0.2.0',
    'notes': '- 更快\n- 更稳',
    'date': '2026-11-20',
    'pageUrl': 'https://github.com/Daiyimo/Maeve/releases/tag/v0.2.0',
    'apkUrl': canDownload
        ? 'https://github.com/Daiyimo/Maeve/releases/download/v0.2.0/Maeve-v0.2.0.apk'
        : null,
    'apkSize': canDownload ? 20 * 1024 * 1024 : null,
    'canDownload': canDownload,
    'downloadUrl': 'https://github.com/Daiyimo/Maeve/releases',
  },
  if (hasUpdate)
    'update': {
      'version': '0.2.0',
      'name': 'Maeve 0.2.0',
      'notes': '- 更快\n- 更稳',
      'date': '2026-11-20',
      'pageUrl': 'https://github.com/Daiyimo/Maeve/releases/tag/v0.2.0',
      'canDownload': canDownload,
      'downloadUrl': 'https://github.com/Daiyimo/Maeve/releases',
    },
  'download': {
    'phase': phase,
    'bytes': 8 * 1024 * 1024,
    'total': 20 * 1024 * 1024,
    'percent': percent,
  },
});

void main() {
  /// 挂真实的壳再推入更新页:测的就是真机上那条路径(含路由触感)。
  Future<FakeUpdateBridge> mount(
    WidgetTester tester,
    FakeUpdateBridge bridge,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    final chat = FakeChatBridge();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final entry = MethodChannel(MaeveBridge.entryEvents.name);
    messenger.setMockMethodCallHandler(entry, (_) async => null);
    // openHostLink 走 status + open_url;这里给一条能打开的链接。
    messenger.setMockMethodCallHandler(MaeveBridge.methods, (call) async {
      return switch (call.method) {
        'status' => {
          'links': {'updates': 'https://example.test/updates'},
        },
        'open_url' => true,
        _ => null,
      };
    });
    final controller = MaeveController(
      bridge: chat,
      settingsBridge: FakeSettingsBridge(mode: 'light'),
    );
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await chat.dispose();
      await bridge.dispose();
      tester.view.reset();
      messenger.setMockMethodCallHandler(entry, null);
      messenger.setMockMethodCallHandler(MaeveBridge.methods, null);
    });
    await tester.pumpWidget(MaeveApp(controller: controller));
    await tester.pumpAndSettle();
    AppPage.open(
      tester.element(find.byType(Scaffold).first),
      UpdatePage(bridge: bridge),
    );
    await tester.pumpAndSettle();
    return bridge;
  }

  testWidgets('进页读状态：当前版本、新版说明与下载按钮都在', (tester) async {
    final bridge = await mount(tester, FakeUpdateBridge(initial: _update()));
    expect(bridge.calls, ['state']);
    expect(find.text('0.1.0-dolores'), findsOneWidget);
    expect(find.text('有新版本'), findsOneWidget);
    expect(find.text('发现新版本 0.2.0'), findsOneWidget);
    expect(find.text('2026-11-20 发布'), findsOneWidget);
    expect(find.text('下载更新'), findsOneWidget);
    // 没在下、也没下好时不该出现「删除下载」。
    expect(find.text('删除下载'), findsNothing);
  });

  testWidgets('从没查过时不冒充「已是最新」', (tester) async {
    await mount(
      tester,
      FakeUpdateBridge(
        initial: AppUpdate.fromMap({
          'available': false,
          'currentVersion': '0.1.0-dolores',
          'status': 'idle',
          'manualRounds': 0,
        }),
      ),
    );
    expect(find.text('尚未检查'), findsOneWidget);
    expect(find.text('已是最新'), findsNothing);
  });

  testWidgets('检查更新：已是最新如实说，连不上也如实说', (tester) async {
    final bridge = await mount(
      tester,
      FakeUpdateBridge(initial: _update(hasUpdate: false)),
    );
    bridge.emit(_update(hasUpdate: false, status: 'done'));
    await tester.tap(find.text('检查更新').last);
    await tester.pumpAndSettle();
    expect(bridge.calls, ['state', 'check']);
    expect(find.text('已是最新版本。'), findsOneWidget);

    bridge.emit(_update(hasUpdate: false, status: 'failed'));
    await tester.tap(find.text('检查更新').last);
    await tester.pumpAndSettle();
    expect(find.text('暂时连不上 GitHub，稍后再试。'), findsOneWidget);
    expect(find.text('连不上 GitHub'), findsOneWidget);
  });

  testWidgets('下载中按钮不可点、显示进度，且能删除下载', (tester) async {
    final bridge = await mount(
      tester,
      FakeUpdateBridge(initial: _update(phase: 'downloading', percent: 40)),
    );
    expect(find.text('正在下载 40%'), findsOneWidget);
    expect(find.text('8.0 / 20.0 MB'), findsOneWidget);
    final button = tester.widget<MaeveGlassTextButton>(
      find.ancestor(
        of: find.text('正在下载 40%'),
        matching: find.byType(MaeveGlassTextButton),
      ),
    );
    expect(button.enabled, isFalse);
    expect(button.onPressed, isNull);
    expect(find.text('删除下载'), findsOneWidget);
    await tester.tap(find.text('删除下载'));
    await tester.pumpAndSettle();
    expect(bridge.calls, ['state', 'cancel']);
  });

  testWidgets('下好了变安装；未授权时先去开未知来源', (tester) async {
    final bridge = await mount(
      tester,
      FakeUpdateBridge(initial: _update(phase: 'ready', percent: 100)),
    );
    expect(find.text('安装更新'), findsOneWidget);
    bridge.installResult = 'permission';
    await tester.tap(find.text('安装更新'));
    await tester.pumpAndSettle();
    expect(bridge.calls, ['state', 'install', 'state']);
    expect(
      find.text('请先允许 Maeve 安装未知应用，回来后再次点「安装更新」。'),
      findsOneWidget,
    );
  });

  testWidgets('校验被拒时把原因说出来，不静默失败', (tester) async {
    final bridge = await mount(tester, FakeUpdateBridge(initial: _update()));
    bridge.failure = PlatformException(
      code: 'apk_rejected',
      message: '这个安装包的签名和已装的 Maeve 不同，不能覆盖安装，已删除。',
    );
    await tester.tap(find.text('下载更新'));
    await tester.pumpAndSettle();
    expect(
      find.text('这个安装包的签名和已装的 Maeve 不同，不能覆盖安装，已删除。'),
      findsOneWidget,
    );
  });

  testWidgets('发布没有可下的包时不给下载，只留浏览器入口', (tester) async {
    await mount(
      tester,
      FakeUpdateBridge(initial: _update(canDownload: false)),
    );
    expect(find.text('这个发布没有可下载的安装包'), findsOneWidget);
    expect(find.text('在浏览器打开'), findsOneWidget);
  });

  testWidgets('下载进度事件到了页面就跟着动，不用手动刷新', (tester) async {
    final bridge = await mount(
      tester,
      FakeUpdateBridge(initial: _update(phase: 'downloading', percent: 10)),
    );
    expect(find.text('正在下载 10%'), findsOneWidget);
    bridge.emit(_update(phase: 'downloading', percent: 70));
    await tester.pumpAndSettle();
    expect(find.text('正在下载 70%'), findsOneWidget);
    bridge.emit(_update(phase: 'ready', percent: 100));
    await tester.pumpAndSettle();
    expect(find.text('安装更新'), findsOneWidget);
  });
}
