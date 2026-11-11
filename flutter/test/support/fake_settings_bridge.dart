import 'package:maeve_flutter/bridge/maeve_settings_bridge.dart';

class FakeSettingsBridge extends MaeveSettingsBridge {
  FakeSettingsBridge({String mode = 'dark', bool configured = true})
    : values = {
        'mode': mode,
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
        'overlay': false,
        'notifications': true,
        'helperRunning': false,
        'phoneSupported': true,
      };
  final Map<String, dynamic> values;
  final targets = <String>[];
  final savedModels = <Map<String, String>>[];
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
}
