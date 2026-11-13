import 'package:maeve_flutter/bridge/maeve_settings_bridge.dart';

class FakeSettingsBridge extends MaeveSettingsBridge {
  final wechatActions = <String>[];
  final wechatStatus = <String, dynamic>{
    'bound': false,
    'enabled': false,
    'executionEnabled': false,
    'amooReady': false,
    'background': false,
    'backgroundReady': false,
    'amooActive': false,
    'state': 'unbound',
    'loggingIn': false,
  };
  @override
  Future<Map<String, dynamic>> wechat(String action, {bool? enabled}) async {
    wechatActions.add(action);
    if (fail) throw StateError('Fixture WeChat failure');
    if (action == 'setBackground') wechatStatus['background'] = enabled;
    if (action == 'setExecution') wechatStatus['executionEnabled'] = enabled;
    if (action == 'setEnabled') wechatStatus['enabled'] = enabled;
    if (action == 'login') wechatStatus['loggingIn'] = true;
    if (action == 'cancelLogin') wechatStatus['loggingIn'] = false;
    if (action == 'unbind') {
      wechatStatus.addAll({
        'bound': false,
        'enabled': false,
        'state': 'unbound',
      });
    }
    return Map.of(wechatStatus);
  }

  FakeSettingsBridge({String mode = 'light', bool configured = true})
    : values = {
        'mode': mode,
        'welcomeSeen': true,
        'capsuleEnabled': true,
        'accent': 'reverie',
        'haptics': true,
        'reduceTransparency': false,
        'currentModelId': configured ? 'cfg-1' : null,
        'modelName': configured ? 'test-model' : null,
        'models': configured
            ? [
                {
                  'id': 'cfg-1',
                  'name': 'Test provider',
                  'model': 'test-model',
                  'kind': 'OpenAiCompat',
                  'baseUrl': 'https://example.test/v1',
                },
              ]
            : <Map<String, Object>>[],
        'accessibility': true,
        'accessibilityRunning': true,
        'overlay': false,
        'notifications': true,
        'helperRunning': false,
        'phoneSupported': true,
        'presets': [
          {
            'id': '阶跃星辰',
            'label': '阶跃星辰',
            'kind': 'OpenAiCompat',
            'baseUrl': 'https://api.stepfun.com/v1',
            'planUrl': 'https://api.stepfun.com/step_plan/v1',
            'modelProfiles': [
              {
                'id': 'step-3.7-flash',
                'vision': true,
                'reasoningLevels': ['low', 'medium', 'high'],
              },
              {
                'id': 'step-3.5-flash',
                'vision': false,
                'reasoningLevels': <String>[],
              },
            ],
            'selfHosted': false,
          },
        ],
      };
  final Map<String, dynamic> values;
  final listRequests = <Map<String, String>>[];
  Map<String, dynamic> listedModels = {'models': <Map<String, dynamic>>[]};
  Map<String, dynamic> usage = {
    'totals': {
      'input': 0,
      'output': 0,
      'cached': 0,
      'calls': 0,
      'unreported': 0,
    },
    'models': [],
  };
  @override
  Future<NativeSettings> completeOnboarding() async {
    if (fail) throw StateError('Fixture welcome save failure');
    values['welcomeSeen'] = true;
    return load();
  }

  @override
  Future<Map<String, dynamic>> listModels(Map<String, String> config) async {
    listRequests.add(Map.of(config));
    if (fail) throw StateError('Fixture catalogue failure');
    return listedModels;
  }

  @override
  Future<Map<String, dynamic>> modelUsage() async {
    if (fail) throw StateError('Fixture usage failure');
    return usage;
  }

  final targets = <String>[];
  final approvalChanges = <bool>[];
  final paceChanges = <String>[];
  @override
  Future<NativeSettings> setCapsuleEnabled(bool enabled) async {
    if (fail) throw StateError('Fixture capsule save failure');
    values['capsuleEnabled'] = enabled;
    return load();
  }

  @override
  Future<NativeSettings> setOperatingPace(String pace) async {
    if (fail) throw StateError('Fixture pace save failure');
    paceChanges.add(pace);
    values['operatingPace'] = pace;
    return load();
  }

  @override
  Future<NativeSettings> setAutonomous(bool enabled) async {
    if (fail) throw StateError('Fixture approval save failure');
    approvalChanges.add(enabled);
    values['autonomous'] = enabled;
    return load();
  }

  final savedModels = <Map<String, String>>[];
  final amooActions = <String>[];
  NativeAmooStatus amooStatus = const NativeAmooStatus(
    installed: true,
    accessibility: true,
  );
  bool fail = false;
  @override
  Future<NativeSettings> load() async {
    if (fail) throw StateError('Fixture read failure');
    return NativeSettings.fromMap(values);
  }

  @override
  Future<NativeSettings> appearance(Map<String, Object> settings) async {
    values.addAll(settings);
    return load();
  }

  @override
  Future<NativeSettings> saveModel(Map<String, String> config) async {
    savedModels.add(Map.of(config));
    final model = {'id': 'cfg-added', ...config}..remove('apiKey');
    values['models'] = [model];
    values['currentModelId'] = 'cfg-added';
    values['modelName'] = config['model'];
    return load();
  }

  @override
  Future<NativeSettings> selectModel(String id) async {
    final models = values['models'] as List;
    final selected = models.cast<Map>().firstWhere((m) => m['id'] == id);
    values['currentModelId'] = id;
    values['modelName'] = selected['model'];
    return load();
  }

  @override
  Future<void> openAccess(String target) async {
    targets.add(target);
  }

  @override
  Future<NativeAmooStatus> amoo(String action) async {
    amooActions.add(action);
    if (fail) throw StateError('Fixture Amoo failure');
    if (action == 'start') {
      amooStatus = const NativeAmooStatus(
        stage: 'waiting',
        installed: true,
        accessibility: true,
        bridgeEnabled: true,
      );
    } else if (action == 'cancel') {
      amooStatus = const NativeAmooStatus(
        installed: true,
        accessibility: true,
        bridgeEnabled: true,
      );
    }
    return amooStatus;
  }
}
