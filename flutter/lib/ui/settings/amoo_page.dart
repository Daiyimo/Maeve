import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../bridge/maeve_settings_bridge.dart';
import '../common/app_page.dart';
import 'settings_components.dart';

class AmooPage extends StatefulWidget {
  const AmooPage({super.key});
  @override
  State<AmooPage> createState() => _AmooPageState();
}

class _AmooPageState extends State<AmooPage> with WidgetsBindingObserver {
  NativeAmooStatus? _status;
  String? _error;
  bool _loading = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_update('snapshot'));
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
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      unawaited(_update('snapshot'));
    }
  }

  Future<void> _update(String action) async {
    if (_loading) return;
    setState(() => _loading = true);
    try {
      final next = await MaeveScope.read(context).settingsBridge.amoo(action);
      if (mounted) {
        setState(() {
          _status = next;
          _error = null;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(
          () => _error = error is PlatformException
              ? error.message ?? '未能读取 Amoo 状态，请重试。'
              : '未能读取 Amoo 状态，请重试。',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String get _stageText => switch (_status?.stage) {
    'waiting' => '等待 Amoo 聊天页，请现在切换过去',
    'sending' => '正在发送接入说明',
    'verifying' => '已发送，等待 Amoo 完成验证',
    'done' => '接入验证已完成',
    'failed' => '接入未完成，请重试并保持 Amoo 在前台',
    _ => '尚未开始接入',
  };

  @override
  Widget build(BuildContext context) {
    final status = _status;
    final last = status?.lastResultAt?.toLocal();
    final latest = last == null
        ? null
        : TimeOfDay.fromDateTime(last).format(context);
    return AppPage.list(
      title: 'Amoo',
      subtitle: '把手机操作交给 Maeve',
      actions: [
        IconButton(
          tooltip: '刷新 Amoo 状态',
          onPressed: _loading ? null : () => _update('snapshot'),
          icon: const Icon(Icons.refresh_rounded, size: 20),
        ),
      ],
      children: [
        const SizedBox(height: 20),
        if (_error != null)
          SettingsNotice(text: _error!, icon: Icons.info_outline),
        SettingsSection(
          title: '一键接入',
          rows: [
            SettingsTile(
              icon: Icons.auto_awesome_outlined,
              title: '接入进度',
              subtitle: status == null ? '正在读取设备状态' : _stageText,
            ),
            if (status != null && !status.installed)
              const SettingsTile(
                icon: Icons.info_outline,
                title: '未检测到 Amoo',
                subtitle: '此接入适用于 StepOS 的 Amoo 聊天应用',
              ),
            if (status != null && !status.accessibility)
              SettingsTile(
                icon: Icons.accessibility_new_rounded,
                title: '开启无障碍服务',
                subtitle: '用于把接入说明填入 Amoo 聊天',
                onTap: () async {
                  try {
                    await MaeveScope.read(context).settingsBridge
                        .openAccess('accessibility');
                  } catch (_) {
                    if (mounted) setState(() => _error = '未能打开无障碍设置，请重试。');
                  }
                },
              ),
            SettingsTile(
              icon: status?.active == true
                  ? Icons.close_rounded
                  : Icons.link_rounded,
              title: status?.active == true ? '取消接入' : '开始接入',
              enabled:
                  !_loading &&
                  status != null &&
                  (status.active || (status.installed && status.accessibility)),
              onTap: () => _update(status?.active == true ? 'cancel' : 'start'),
            ),
          ],
          footnote: '开始后请切到 Amoo 聊天。Maeve 会填入并发送技能接入说明，再等待文件桥验证。',
        ),
        SettingsSection(
          title: '文件桥',
          rows: [
            SettingsTile(
              icon: Icons.swap_horiz_rounded,
              title: '文件桥状态',
              value: status == null
                  ? '读取中'
                  : status.bridgeEnabled
                  ? '已开启'
                  : '未开启',
            ),
            SettingsTile(
              icon: Icons.history_rounded,
              title: '最近收到的结果',
              subtitle: latest == null ? '尚无结果记录' : '最近一次结果：$latest',
            ),
          ],
          footnote: '文件桥开启表示正在接收请求，不代表已通过接入验证。取消只停止本次接入，已开启的文件桥会保留。',
        ),
      ],
    );
  }
}
