import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../bridge/maeve_settings_bridge.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import 'settings_components.dart';

class AddModelPage extends StatefulWidget {
  const AddModelPage({super.key, this.config});
  final NativeModelConfig? config;
  @override
  State<AddModelPage> createState() => _AddModelPageState();
}

class _AddModelPageState extends State<AddModelPage> {
  static const _api = 'https://api.stepfun.com/v1';
  static const _plan = 'https://api.stepfun.com/step_plan/v1';
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController(),
      _url = TextEditingController(),
      _model = TextEditingController(),
      _key = TextEditingController();
  bool _saving = false, _fetching = false, _vision = false;
  int _generation = 0;
  String _effort = 'default';
  String _kind = 'OpenAiCompat';
  String? _error, _catalogNote;
  List<Map<String, dynamic>> _models = [];
  List<String> _levels = [];
  NativeProviderPreset? get _preset {
    final presets = MaeveScope.read(context).native.presets;
    return presets.isEmpty ? null : presets.first;
  }

  String get _apiUrl => _preset?.baseUrl ?? _api;
  String get _planUrl => _preset?.planUrl ?? _plan;
  bool get _retainedKey {
    final original = widget.config;
    if (original == null || !original.hasKey || _kind != original.kind) {
      return false;
    }
    final url = _url.text.trim().replaceFirst(RegExp(r'/+$'), '');
    return url == original.baseUrl ||
        {_api, _plan}.contains(url) && {_api, _plan}.contains(original.baseUrl);
  }

  bool get _canFetch => _key.text.trim().isNotEmpty || _retainedKey;

  @override
  void initState() {
    super.initState();
    final config = widget.config;
    _name.text = config?.name ?? '阶跃星辰';
    _kind = config?.kind ?? 'OpenAiCompat';
    _url.text = config?.baseUrl ?? _api;
    _model.text = config?.model ?? '';
    _vision = config?.vision ?? false;
    _effort = config?.reasoningEffort ?? 'default';
    _levels = config?.reasoningLevels ?? [];
    if (_effort != 'default' && !_levels.contains(_effort)) {
      _effort = 'default';
      _catalogNote = '先前的思考强度已不在此模型的选项中，保存后使用模型默认。';
    }
  }

  @override
  void dispose() {
    _generation++;
    for (final field in [_name, _url, _model, _key]) {
      field.dispose();
    }
    super.dispose();
  }

  InputDecoration _decoration(
    BuildContext context,
    String label, [
    String? hint,
  ]) {
    final c = MaeveTheme.of(context).colors;
    final shape = BorderRadius.circular(18);
    return InputDecoration(
      labelText: label,
      hintText: hint,
      filled: true,
      fillColor: c.glassCard,
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
      border: OutlineInputBorder(borderRadius: shape),
      enabledBorder: OutlineInputBorder(
        borderRadius: shape,
        borderSide: BorderSide(color: c.glassEdge, width: .75),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: shape,
        borderSide: BorderSide(color: c.accent, width: 1.25),
      ),
    );
  }

  Map<String, String> _values() => {
    if (widget.config != null) 'id': widget.config!.id,
    'name': _name.text.trim(),
    'baseUrl': _url.text.trim(),
    'model': _model.text.trim(),
    'apiKey': _key.text.trim(),
    'kind': _kind,
    'vision': '$_vision',
    'reasoningEffort': _effort,
  };
  void _invalidateCatalog({bool clearKey = false}) {
    _generation++;
    _fetching = false;
    _models = [];
    _catalogNote = null;
    _error = null;
    if (clearKey) _key.clear();
  }

  void _applyProfile(String id, [Map<String, dynamic>? listed]) {
    Map<String, dynamic>? profile = listed;
    if (profile == null &&
        {
          _api,
          _plan,
        }.contains(_url.text.trim().replaceFirst(RegExp(r'/+$'), ''))) {
      for (final p in _preset?.modelProfiles ?? <Map<String, dynamic>>[]) {
        if (p['id'] == id) {
          profile = p;
          break;
        }
      }
    }
    _vision = profile?['vision'] == true;
    _levels = List<String>.from(
      profile?['reasoningLevels'] as List? ?? const [],
    );
    if (!_levels.contains(_effort)) _effort = 'default';
  }

  Future<void> _fetchModels() async {
    if (_fetching || !_canFetch) return;
    FocusScope.of(context).unfocus();
    final values = _values();
    final url = Uri.tryParse(values['baseUrl']!);
    if (url == null ||
        !['http', 'https'].contains(url.scheme) ||
        url.host.isEmpty) {
      setState(() => _error = '请填写有效的 API 地址');
      return;
    }
    final generation = ++_generation;
    setState(() {
      _fetching = true;
      _error = null;
      _catalogNote = null;
    });
    try {
      final result = await MaeveScope.read(context).settingsBridge
          .listModels(values);
      if (!mounted || generation != _generation) return;
      setState(() {
        _models = [
          for (final row in result['models'] as List? ?? const [])
            Map<String, dynamic>.from(row as Map),
        ];
        _catalogNote =
            result['error'] as String? ??
            (_models.isEmpty ? '此站点未返回模型列表，你仍可以手动填写模型名称。' : null);
      });
    } on PlatformException catch (e) {
      if (mounted && generation == _generation) {
        setState(() => _error = e.message ?? '获取模型失败，请重试或手动填写。');
      }
    } catch (_) {
      if (mounted && generation == _generation) {
        setState(() => _error = '获取模型失败，请重试或手动填写。');
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _fetching = false);
      }
    }
  }

  Future<void> _save() async {
    if (_saving || !_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await MaeveScope.read(context).saveModel(_values());
      _key.clear();
      if (mounted) Navigator.of(context).pop();
    } on PlatformException catch (e) {
      if (mounted) setState(() => _error = e.message ?? '保存失败，请重试');
    } catch (_) {
      if (mounted) setState(() => _error = '保存失败，请重试');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final isPlan =
        _url.text.trim().replaceFirst(RegExp(r'/+$'), '') == _planUrl;
    return AppPage.list(
      title: widget.config == null ? '添加模型' : '编辑模型',
      subtitle: '配置思考与看图的方式',
      children: [
        const SizedBox(height: 24),
        Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SettingsSection(
                title: '服务商预设',
                rows: const [
                  SettingsRow(
                    title: '阶跃星辰',
                    subtitle: '一个接入，选择 API 或 Plan',
                    icon: Icons.auto_awesome_outlined,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: _name,
                enabled: !_saving,
                decoration: _decoration(context, '接入名称（可选）'),
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'API 地址',
                      style: MaeveType.bodyStrong.copyWith(color: c.ink),
                    ),
                  ),
                  SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: false, label: Text('API')),
                      ButtonSegment(value: true, label: Text('Plan')),
                    ],
                    selected: {isPlan},
                    showSelectedIcon: false,
                    onSelectionChanged: _saving
                        ? null
                        : (value) => setState(() {
                            _invalidateCatalog(
                              clearKey: !{
                                _api,
                                _plan,
                              }.contains(_url.text.trim()),
                            );
                            _url.text = value.single ? _planUrl : _apiUrl;
                            _kind = 'OpenAiCompat';
                          }),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextFormField(
                key: const ValueKey('model-base-url'),
                controller: _url,
                enabled: !_saving,
                keyboardType: TextInputType.url,
                autocorrect: false,
                decoration: _decoration(context, '基础地址', 'https://…'),
                onChanged: (_) =>
                    setState(() => _invalidateCatalog(clearKey: true)),
                validator: (v) {
                  final u = Uri.tryParse(v?.trim() ?? '');
                  return u != null &&
                          ['http', 'https'].contains(u.scheme) &&
                          u.host.isNotEmpty &&
                          u.userInfo.isEmpty
                      ? null
                      : '请输入有效的 HTTP(S) 地址';
                },
              ),
              const SizedBox(height: 20),
              TextFormField(
                key: const ValueKey('model-api-key'),
                controller: _key,
                enabled: !_saving,
                obscureText: true,
                enableSuggestions: false,
                autocorrect: false,
                decoration: _decoration(
                  context,
                  'API Key',
                  _retainedKey ? '留空保留已保存的 Key' : '输入此接入的 Key',
                ),
                onChanged: (_) => setState(() => _invalidateCatalog()),
                validator: (v) => (v?.trim().isNotEmpty == true || _retainedKey)
                    ? null
                    : '请填写 API Key',
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                key: const ValueKey('fetch-models'),
                onPressed: !_saving && !_fetching && _canFetch
                    ? _fetchModels
                    : null,
                icon: const Icon(Icons.sync_rounded, size: 18),
                label: Text(_fetching ? '正在获取…' : '获取模型'),
              ),
              if (_catalogNote != null)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: SettingsNotice(
                    text: _catalogNote!,
                    icon: Icons.info_outline,
                  ),
                ),
              const SizedBox(height: 20),
              if (_models.isNotEmpty) ...[
                DropdownButtonFormField<String>(
                  key: ValueKey('listed-models-$_generation'),
                  isExpanded: true,
                  borderRadius: BorderRadius.circular(18),
                  menuMaxHeight: 380,
                  style: MaeveType.body.copyWith(color: c.ink),
                  decoration: _decoration(context, '选择获取的模型'),
                  items: [
                    for (final model in _models)
                      DropdownMenuItem(
                        value: model['id'] as String,
                        child: Text(
                          model['name'] as String? ?? model['id'] as String,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                  onChanged: _saving
                      ? null
                      : (id) {
                          if (id == null) return;
                          setState(() {
                            _model.text = id;
                            _applyProfile(
                              id,
                              _models.firstWhere((m) => m['id'] == id),
                            );
                          });
                        },
                ),
                const SizedBox(height: 16),
              ],
              TextFormField(
                key: const ValueKey('model-name'),
                controller: _model,
                enabled: !_saving,
                autocorrect: false,
                decoration: _decoration(context, '模型名称', '选择列表中的模型，或手动填写 ID'),
                onChanged: (value) =>
                    setState(() => _applyProfile(value.trim())),
                validator: (v) =>
                    v?.trim().isNotEmpty == true ? null : '请填写模型名称',
              ),
              const SizedBox(height: 20),
              SettingsSection(
                title: '模型能力',
                rows: [
                  const SettingsRow(
                    title: '工具调用',
                    subtitle: '已启用，Maeve 通过工具执行手机任务',
                    icon: Icons.touch_app_outlined,
                  ),
                  SettingsSwitchTile(
                    key: const ValueKey('model-vision'),
                    title: '图片输入',
                    subtitle: '允许模型接收图片与执行画面',
                    icon: Icons.image_outlined,
                    value: _vision,
                    enabled: !_saving,
                    onChanged: (value) => setState(() => _vision = value),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              DropdownButtonFormField<String>(
                key: ValueKey('model-reasoning-${_levels.join(',')}-$_effort'),
                initialValue: _effort,
                isExpanded: true,
                borderRadius: BorderRadius.circular(18),
                menuMaxHeight: 380,
                style: MaeveType.body.copyWith(color: c.ink),
                decoration: _decoration(context, '思考强度'),
                items: [
                  const DropdownMenuItem(value: 'default', child: Text('模型默认')),
                  for (final level in _levels)
                    DropdownMenuItem(
                      value: level,
                      child: Text(switch (level) {
                        'low' => '轻量 · 更快响应',
                        'medium' => '均衡 · 日常任务',
                        'high' => '深入 · 复杂规划',
                        _ => level,
                      }),
                    ),
                ],
                onChanged: _saving
                    ? null
                    : (value) => setState(() => _effort = value ?? 'default'),
              ),
              if (_levels.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: Text(
                    '此模型未公布可选档位，沿用模型默认。',
                    style: MaeveType.hint.copyWith(color: c.ink3),
                  ),
                ),
              const SizedBox(height: 24),
              if (_error != null) ...[
                Text(_error!, style: MaeveType.caption.copyWith(color: c.bad)),
                const SizedBox(height: 16),
              ],
              FilledButton(
                onPressed: _saving ? null : _save,
                child: Text(_saving ? '正在保存…' : '保存并使用'),
              ),
              const SizedBox(height: 16),
              Text(
                '获取列表不会发起对话。保存后，所选能力和思考强度用于实际任务。',
                style: MaeveType.caption.copyWith(color: c.ink2, height: 1.6),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ],
    );
  }
}
