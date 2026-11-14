import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../bridge/maeve_settings_bridge.dart';
import '../common/app_page.dart';
import '../common/selection_field.dart';
import 'settings_components.dart';

class AmooWechatPage extends StatefulWidget {
  const AmooWechatPage({super.key});
  @override
  State<AmooWechatPage> createState() => _AmooWechatPageState();
}

class _AmooWechatPageState extends State<AmooWechatPage>
    with WidgetsBindingObserver {
  MaeveSettingsBridge? _bridge;
  Map<String, dynamic>? _status;
  String? _error;
  bool _busy = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _bridge = MaeveScope.read(context).settingsBridge;
      unawaited(_update('snapshot'));
    });
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (mounted &&
          ModalRoute.of(context)?.isCurrent == true &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        unawaited(_update('snapshot'));
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      unawaited(_update('snapshot'));
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    final bridge = _bridge;
    if (bridge != null) {
      unawaited(
        bridge.wechat('cancelLogin').catchError((_) => <String, dynamic>{}),
      );
    }
    super.dispose();
  }

  Future<void> _update(String action, {bool? enabled}) async {
    final bridge = _bridge;
    if (_busy || bridge == null) return;
    setState(() => _busy = true);
    try {
      final status = await bridge.wechat(action, enabled: enabled);
      if (mounted) {
        setState(() {
          _status = status;
          _error = null;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(
          () => _error = error is PlatformException
              ? error.message ?? '微信连接更新失败，请重试'
              : '微信连接更新失败，请重试',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _unbind() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('解绑微信？'),
        content: const Text('停止接收微信消息并清除本机绑定。之后可以重新扫码连接。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('解绑'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) await _update('unbind');
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    final background = status?['background'] == true;
    final bound = status?['bound'] == true;
    final loggingIn = status?['loggingIn'] == true;
    final qr = status?['qrPng'];
    final state = switch (status?['state']) {
      'connected' => '微信已连接',
      'connecting' => '正在连接微信',
      'failed' => '微信连接中断，正在重试',
      'off' => '已暂停接收',
      'unbound' when bound => '微信授权已失效，请解绑后重新扫码',
      _ => '尚未绑定微信',
    };
    return AppPage.list(
      title: '微信连接',
      subtitle: '在微信里和 Amoo 对话',
      actions: [
        IconButton(
          tooltip: '刷新微信状态',
          onPressed: _busy ? null : () => _update('snapshot'),
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
      children: [
        if (_error != null)
          SettingsNotice(text: _error!, icon: Icons.info_outline),
        if (status?['error'] is String)
          SettingsNotice(
            text: status!['error'] as String,
            icon: Icons.info_outline,
          ),
        if (status?['relayError'] is String)
          SettingsNotice(
            text: status!['relayError'] as String,
            icon: Icons.info_outline,
          ),
        SettingsSection(
          title: '连接状态',
          topSpacing: 12,
          rows: [
            SettingsTile(
              icon: Icons.restart_alt_rounded,
              title: '打开系统 Amoo',
              subtitle: '使用手机里的 Amoo 真实聊天入口',
              enabled: !_busy && status != null && status['amooActive'] != true,
              onTap: () => _update('openAmoo'),
            ),
            SettingsTile(
              icon: Icons.chat_bubble_outline_rounded,
              title: status == null ? '正在读取微信状态' : state,
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text('系统 Amoo'),
                  const SizedBox(height: 10),
                  MaeveSegments<bool>(
                    value: background,
                    options: const {false: '前台', true: '后台'},
                    disabled: status?['backgroundReady'] == true
                        ? const {}
                        : const {true},
                    onChanged:
                        _busy || status == null || status['amooActive'] == true
                        ? null
                        : (value) => _update('setBackground', enabled: value),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    status?['backgroundReady'] != true
                        ? '后台接入需要连接手机助手'
                        : background
                        ? '在独立屏幕处理，不切换当前界面'
                        : '收到消息时打开系统 Amoo',
                  ),
                ],
              ),
            ),
          ],
          footnote: '聊天由系统 Amoo 处理，不使用 Maeve 模型 API。',
        ),
        if (!bound)
          SettingsSection(
            title: '绑定微信',
            rows: [
              SettingsTile(
                icon: loggingIn ? Icons.close_rounded : Icons.qr_code_rounded,
                title: loggingIn ? '取消扫码' : '获取绑定二维码',
                enabled: !_busy && status != null,
                onTap: () => _update(loggingIn ? 'cancelLogin' : 'login'),
              ),
            ],
          ),
        if (qr is Uint8List)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Image.memory(
                  qr,
                  width: 240,
                  height: 240,
                  filterQuality: FilterQuality.none,
                  semanticLabel: '微信 Bot 绑定二维码',
                ),
                const SizedBox(height: 12),
                const Text(
                  '使用微信扫码并确认。可截图后在微信扫一扫中从相册识别。',
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        if (bound)
          SettingsSection(
            title: '微信消息',
            rows: [
              SettingsSwitchTile(
                icon: Icons.mark_chat_unread_outlined,
                title: '接收微信消息',
                subtitle: '转给 Amoo 并将回复发回微信',
                value: status?['enabled'] == true,
                enabled: !_busy,
                onChanged: (value) => _update('setEnabled', enabled: value),
              ),
              SettingsTile(
                icon: Icons.link_off_rounded,
                title: '解绑微信',
                enabled: !_busy,
                onTap: _unbind,
              ),
            ],
          ),
        SettingsSection(
          title: '手机任务',
          rows: [
            SettingsSwitchTile(
              icon: Icons.phonelink_ring_outlined,
              title: '通过 Maeve 执行任务',
              subtitle: '允许 Amoo 把手机操作交给 Maeve',
              value: status?['executionEnabled'] == true,
              enabled: !_busy && status != null,
              onChanged: (value) => _update('setExecution', enabled: value),
            ),
          ],
          footnote: '关闭后仍可在微信与 Amoo 聊天，但 Amoo 的新请求不会启动 Maeve 手机任务。已开始的任务请在执行记录中取消。开始 Amoo 接入会开启文件桥，接入完成后可在这里关闭。',
        ),
      ],
    );
  }
}
