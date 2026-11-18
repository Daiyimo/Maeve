import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import '../../widgets/glass_surface.dart';
import 'settings_components.dart';
import 'system_access_page.dart';

/// 手机操作的一项前置条件:用途、当前状态、去哪儿补。
class PhoneReadinessItem {
  const PhoneReadinessItem({
    required this.icon,
    required this.title,
    required this.why,
    required this.status,
    required this.done,
    this.target,
  });

  final IconData icon;
  final String title;

  /// 这项权限/开关用来做什么——引导要讲清楚"为什么需要它"。
  final String why;
  final String status;
  final bool done;

  /// openAccess 的跳转目标;null = 纯状态项(比如等助手自动连接)。
  final String? target;
}

/// 手机操作就绪度:把原生状态位翻译成一份可执行的授权清单。
/// 设置页顶部的状态行与引导页共用同一份判定,不会各说各话。
///
/// 判定口径:无障碍已连接 + 助手运行中是核心;悬浮窗与通知强烈建议
/// (胶囊、确认卡与配对码都靠它们)。开发者选项/无线调试是**手段**不是目的:
/// 助手已配对或运行中(比如经电脑 USB 启动)就不再算待办。
class PhoneReadiness {
  const PhoneReadiness({
    required this.permissions,
    required this.connection,
    required this.loaded,
    required this.error,
    required this.phoneSupported,
    required this.developerPageMissing,
    required this.wirelessMissing,
  });

  final List<PhoneReadinessItem> permissions;
  final List<PhoneReadinessItem> connection;
  final bool loaded;
  final String? error;
  final bool phoneSupported;
  final bool developerPageMissing;
  final bool wirelessMissing;

  List<PhoneReadinessItem> get items => [...permissions, ...connection];
  int get pending => items.where((i) => !i.done).length;
  bool get ready => loaded && error == null && pending == 0;

  /// 第一个还没完成、又有地方可去的项——引导页的"下一步"按钮。
  PhoneReadinessItem? get next {
    for (final item in items) {
      if (!item.done && item.target != null) return item;
    }
    return null;
  }

  factory PhoneReadiness.of(MaeveController controller) {
    final s = controller.native;
    final loaded = controller.settingsLoaded;
    final error = controller.settingsError;
    final helperPast = s.helperRunning || s.helperPaired;
    String state(bool done, String yes, String no) => !loaded
        ? '读取中'
        : error != null
        ? '暂不可读'
        : done
        ? yes
        : no;

    return PhoneReadiness(
      loaded: loaded,
      error: error,
      phoneSupported: s.phoneSupported,
      developerPageMissing: s.developerOptionsPageMissing,
      wirelessMissing: s.wirelessDebuggingMissing,
      permissions: [
        PhoneReadinessItem(
          icon: Icons.accessibility_new_rounded,
          title: '无障碍服务',
          why: '读取界面并执行操作',
          status: !loaded
              ? '读取中'
              : error != null
              ? '暂不可读'
              : s.accessibilityRunning
              ? '已连接'
              : s.accessibility
              ? '等待连接'
              : '未开启',
          done: s.accessibilityRunning,
          target: 'accessibility',
        ),
        PhoneReadinessItem(
          icon: Icons.picture_in_picture_alt_outlined,
          title: '悬浮窗',
          why: '显示任务进度与确认卡',
          status: state(s.overlay, '已允许', '未允许'),
          done: s.overlay,
          target: 'overlay',
        ),
        PhoneReadinessItem(
          icon: Icons.notifications_outlined,
          title: '通知',
          why: '任务结果、确认与配对码通知',
          status: state(s.notifications, '已允许', '未允许'),
          done: s.notifications,
          target: 'notifications',
        ),
      ],
      connection: s.phoneSupported
          ? [
              PhoneReadinessItem(
                icon: Icons.developer_mode_rounded,
                title: '开发者选项',
                why: '无线调试在开发者选项里',
                status: state(
                  s.developerOptions || helperPast,
                  s.developerOptions ? '已开启' : '助手已连接',
                  '未开启',
                ),
                done: s.developerOptions || helperPast,
                target: 'developer',
              ),
              PhoneReadinessItem(
                icon: Icons.wifi_tethering_rounded,
                title: '无线调试',
                why: '手机助手经它连上 Maeve（需连着 Wi-Fi）',
                status: state(
                  s.wirelessDebugging || helperPast,
                  s.wirelessDebugging ? '已开启' : '助手已连接',
                  s.wirelessDebuggingMissing ? '系统未提供' : '未开启',
                ),
                done: s.wirelessDebugging || helperPast,
                // 系统不带无线调试时没有可跳的配对页;通知未允许时先补通知
                // (配对码要在系统配对页输入,与系统授权页同一口径)。
                target: s.wirelessDebuggingMissing && !helperPast
                    ? null
                    : (s.notifications ? 'pairing' : 'notifications'),
              ),
              PhoneReadinessItem(
                icon: Icons.phonelink_ring_outlined,
                title: '后台操作连接',
                why: '在后台执行手机操作的内置助手',
                status: state(
                  s.helperRunning,
                  '运行中',
                  s.helperPaired ? '等待连接' : '未配对',
                ),
                done: s.helperRunning,
                target: s.helperPaired
                    ? null
                    : (s.notifications ? 'pairing' : 'notifications'),
              ),
            ]
          : const [],
    );
  }
}

/// 手机操作状态检测与授权引导:新装用户在这里一眼看到缺什么、为什么需要、
/// 去哪儿补。授权发生在系统设置里:回到前台立刻重读,停在页面上也慢速轮询,
/// 配对成功/助手连上不用手动刷新。
class PhoneReadinessPage extends StatefulWidget {
  const PhoneReadinessPage({super.key});
  @override
  State<PhoneReadinessPage> createState() => _PhoneReadinessPageState();
}

class _PhoneReadinessPageState extends State<PhoneReadinessPage>
    with WidgetsBindingObserver {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _timer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (mounted &&
          ModalRoute.of(context)?.isCurrent == true &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        unawaited(MaeveScope.read(context).refreshSettings());
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
      unawaited(MaeveScope.read(context).refreshSettings());
    }
  }

  Future<void> _open(String target) async {
    try {
      await MaeveScope.read(context).settingsBridge.openAccess(target);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('未能打开系统设置，请重试')));
      }
    }
  }

  SettingsTile _tile(PhoneReadinessItem item) => SettingsTile(
    icon: item.icon,
    title: item.title,
    subtitle: item.why,
    value: item.status,
    onTap: item.target == null ? null : () => _open(item.target!),
  );

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final theme = MaeveTheme.of(context);
    final c = theme.colors;
    final readiness = PhoneReadiness.of(controller);
    final next = readiness.next;
    final developer = readiness.connection.isNotEmpty
        ? readiness.connection[0]
        : null;
    final wireless = readiness.connection.length > 1
        ? readiness.connection[1]
        : null;
    return AppPage.list(
      title: '手机操作状态检测',
      subtitle: '手机操作需要的权限与连接，逐项补齐',
      actions: [
        MaeveGlassButton(
          icon: Icons.refresh_rounded,
          tooltip: '重新检测',
          compact: true,
          haptic: MaeveHaptic.tick,
          onPressed: controller.refreshSettings,
        ),
      ],
      children: [
        const SizedBox(height: 20),
        if (readiness.error != null)
          SettingsNotice(text: readiness.error!, icon: Icons.info_outline),
        // 紫色玻璃头卡:用强调色洗一层,读作"紫色块",跟下面中性分组卡拉开层次。
        // 语义色(琥珀/绿)在奶白玻璃上读作"黄块"难看,统一用强调色更贴气质。
        MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          tint: c.accent,
          tintAlpha: .16,
          padding: const EdgeInsets.all(MaeveSpace.s20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 图标走今日色彩(默认中性=强调色),状态由染色洗层与文字表达。
                  IconTile(
                    icon: readiness.ready
                        ? Icons.check_circle_outline
                        : Icons.health_and_safety_outlined,
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          !readiness.loaded
                              ? '正在读取设备状态…'
                              : readiness.ready
                              ? '手机操作已就绪'
                              : '还有 ${readiness.pending} 项未完成',
                          style: MaeveType.bodyStrong.copyWith(
                            color: c.ink,
                            fontSize: 16,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          readiness.ready
                              ? '无障碍服务与后台助手都在运行，权限齐备。'
                              : '按下面的清单逐项补齐，每完成一项回到这里会自动重新检测。',
                          style: MaeveType.caption.copyWith(color: c.ink2),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              if (next != null) ...[
                const SizedBox(height: MaeveSpace.s12),
                SizedBox(
                  width: double.infinity,
                  child: MaeveGlassTextButton(
                    label: '去完成：${next.title}',
                    icon: next.icon,
                    prominent: true,
                    haptic: MaeveHaptic.light,
                    onPressed: () => _open(next.target!),
                  ),
                ),
              ],
            ],
          ),
        ),
        SettingsSection(
          title: '设备权限',
          rows: [for (final item in readiness.permissions) _tile(item)],
        ),
        if (readiness.phoneSupported)
          SettingsSection(
            title: '手机操作连接',
            rows: [
              for (final item in readiness.connection) _tile(item),
              if (developer != null && !developer.done)
                SettingsTile(
                  icon: Icons.info_outline_rounded,
                  title: '关于手机（版本号）',
                  subtitle: '连续点击版本号 7 次，开启开发者选项',
                  onTap: () => _open('about'),
                ),
              if (wireless != null &&
                  !wireless.done &&
                  !readiness.wirelessMissing)
                SettingsTile(
                  icon: Icons.wifi_rounded,
                  title: 'Wi-Fi 设置',
                  subtitle: '无线调试需要连着 Wi-Fi，不用能上网',
                  onTap: () => _open('wifi'),
                ),
            ],
            footnote:
                '完整流程：① 在「关于手机」连续点击版本号 7 次，开启开发者选项；'
                '② 在开发者选项里打开「无线调试」；③ 选择「使用配对码配对设备」——'
                '无障碍服务运行时会自动读码，也可以从 Maeve 的配对通知输入；'
                '④ 配对成功后助手自动连接。之后重启手机或更新 Maeve，'
                '只要无线调试开着就会自动恢复。',
          ),
        if (!readiness.phoneSupported)
          const SettingsNotice(
            text: '手机操作需要 Android 12 或更新版本。此设备可以改用电脑经 USB 启动手机助手。',
            icon: Icons.info_outline,
          ),
        if (readiness.wirelessMissing && wireless != null && !wireless.done)
          const SettingsNotice(
            text: '系统未提供无线调试：可用电脑经 USB 启动手机助手，启动后这里会显示运行中。',
            icon: Icons.info_outline,
          ),
        if (readiness.developerPageMissing && !readiness.ready)
          const SettingsNotice(
            text: '此运行环境打不开系统开发者选项页，无线配对可能不可用；可用电脑经 USB 启动手机助手。',
            icon: Icons.info_outline,
          ),
        SettingsSection(
          title: '更多',
          rows: [
            SettingsTile(
              icon: Icons.tune_rounded,
              title: '系统授权详情',
              subtitle: '胶囊开关、操作确认方式与 Android 应用设置',
              onTap: () => AppPage.open(context, const SystemAccessPage()),
            ),
          ],
        ),
      ],
    );
  }
}
