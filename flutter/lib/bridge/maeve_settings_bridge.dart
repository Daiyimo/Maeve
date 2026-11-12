import 'package:flutter/services.dart';

class NativeAmooStatus {
  const NativeAmooStatus({
    this.stage = 'idle',
    this.installed = false,
    this.accessibility = false,
    this.bridgeEnabled = false,
    this.lastResultAt,
  });
  final String stage;
  final bool installed, accessibility, bridgeEnabled;
  final DateTime? lastResultAt;
  bool get active => const ['waiting', 'sending', 'verifying'].contains(stage);
  factory NativeAmooStatus.fromMap(Map<dynamic, dynamic> map) =>
      NativeAmooStatus(
        stage: map['stage'] as String? ?? 'idle',
        installed: map['installed'] == true,
        accessibility: map['accessibility'] == true,
        bridgeEnabled: map['bridgeEnabled'] == true,
        lastResultAt: map['lastResultAt'] is num
            ? DateTime.fromMillisecondsSinceEpoch(
                (map['lastResultAt'] as num).toInt(),
              )
            : null,
      );
}

class NativeProviderPreset {
  const NativeProviderPreset({
    required this.id,
    required this.label,
    required this.kind,
    required this.baseUrl,
    this.selfHosted = false,
  });
  final String id, label, kind, baseUrl;
  final bool selfHosted;
  factory NativeProviderPreset.fromMap(Map<dynamic, dynamic> map) =>
      NativeProviderPreset(
        id: map['id'] as String,
        label: map['label'] as String,
        kind: map['kind'] as String,
        baseUrl: map['baseUrl'] as String,
        selfHosted: map['selfHosted'] == true,
      );
}

class NativeModelConfig {
  const NativeModelConfig({
    required this.id,
    required this.name,
    required this.model,
    required this.kind,
    required this.baseUrl,
  });
  final String id, name, model, kind, baseUrl;
  factory NativeModelConfig.fromMap(Map<dynamic, dynamic> map) =>
      NativeModelConfig(
        id: map['id'] as String,
        name: map['name'] as String? ?? '',
        model: map['model'] as String,
        kind: map['kind'] as String,
        baseUrl: map['baseUrl'] as String,
      );
}

class NativeSettings {
  const NativeSettings({
    this.mode = 'dark',
    this.accent = 'reverie',
    this.haptics = true,
    this.reduceTransparency = false,
    this.currentModelId,
    this.modelName,
    this.models = const [],
    this.presets = const [],
    this.autonomous = false,
    this.accessibility = false,
    this.accessibilityRunning = false,
    this.overlay = false,
    this.notifications = false,
    this.helperRunning = false,
    this.phoneSupported = false,
  });
  final String mode, accent;
  final bool haptics,
      autonomous,
      reduceTransparency,
      accessibility,
      accessibilityRunning,
      overlay,
      notifications,
      helperRunning,
      phoneSupported;
  final String? currentModelId, modelName;
  final List<NativeModelConfig> models;
  final List<NativeProviderPreset> presets;
  factory NativeSettings.fromMap(Map<dynamic, dynamic> map) => NativeSettings(
    mode: map['mode'] as String? ?? 'dark',
    accent: map['accent'] as String? ?? 'reverie',
    autonomous: map['autonomous'] == true,
    haptics: map['haptics'] as bool? ?? true,
    reduceTransparency: map['reduceTransparency'] as bool? ?? false,
    currentModelId: map['currentModelId'] as String?,
    modelName: map['modelName'] as String?,
    models: [
      for (final item in map['models'] as List<dynamic>? ?? const [])
        NativeModelConfig.fromMap(item as Map),
    ],
    presets: [
      for (final item in map['presets'] as List<dynamic>? ?? const [])
        NativeProviderPreset.fromMap(item as Map),
    ],
    accessibility: map['accessibility'] == true,
    accessibilityRunning: map['accessibilityRunning'] == true,
    overlay: map['overlay'] == true,
    notifications: map['notifications'] == true,
    helperRunning: map['helperRunning'] == true,
    phoneSupported: map['phoneSupported'] == true,
  );
}

abstract class MaeveSettingsBridge {
  const MaeveSettingsBridge();
  Future<NativeSettings> load();
  Future<NativeSettings> appearance(Map<String, Object> values);
  Future<NativeSettings> saveModel(Map<String, String> values);
  Future<NativeSettings> selectModel(String id);
  Future<void> openAccess(String target);
  Future<NativeAmooStatus> amoo(String action);
  Future<NativeSettings> setAutonomous(bool enabled);
}

class HostSettingsBridge extends MaeveSettingsBridge {
  const HostSettingsBridge();
  static const channel = MethodChannel('maeve/settings');
  Future<NativeSettings> _call(
    String name, [
    Map<String, Object>? values,
  ]) async {
    final response = await channel.invokeMapMethod<Object?, Object?>(
      name,
      values,
    );
    if (response == null) throw StateError('Native settings were not returned');
    return NativeSettings.fromMap(response);
  }

  @override
  Future<NativeSettings> load() => _call('snapshot');
  @override
  Future<NativeSettings> setAutonomous(bool enabled) =>
      _call('setAutonomous', {'enabled': enabled});
  @override
  Future<NativeSettings> appearance(Map<String, Object> values) =>
      _call('setAppearance', values);
  @override
  Future<NativeSettings> saveModel(Map<String, String> values) =>
      _call('saveModel', values);
  @override
  Future<NativeSettings> selectModel(String id) =>
      _call('selectModel', {'id': id});
  @override
  Future<void> openAccess(String target) =>
      channel.invokeMethod<void>('openAccess', {'target': target});
  @override
  Future<NativeAmooStatus> amoo(String action) async {
    final response = await channel.invokeMapMethod<Object?, Object?>('amoo', {
      'action': action,
    });
    if (response == null) throw StateError('Amoo status was not returned');
    return NativeAmooStatus.fromMap(response);
  }
}
