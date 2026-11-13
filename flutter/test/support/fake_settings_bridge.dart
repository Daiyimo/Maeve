import 'package:maeve_flutter/bridge/maeve_settings_bridge.dart';

class FakeSettingsBridge extends MaeveSettingsBridge {
  FakeSettingsBridge({String mode = 'dark', bool configured = true})
    : values = {
        'mode': mode,
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
            'id': 'DeepSeek',
            'label': 'DeepSeek',
            'kind': 'OpenAiCompat',
            'baseUrl': 'https://api.deepseek.com',
            'selfHosted': false,
          },
          {
            'id': 'OpenAI',
            'label': 'OpenAI',
            'kind': 'OpenAiResponses',
            'baseUrl': 'https://api.openai.com/v1',
            'selfHosted': false,
          },
          {
            'id': 'custom',
            'label': '自定义',
            'kind': 'OpenAiCompat',
            'baseUrl': '',
            'selfHosted': false,
          },
        ],
      };
  final Map<String, dynamic> values;
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
