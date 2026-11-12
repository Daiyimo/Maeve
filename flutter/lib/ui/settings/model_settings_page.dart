import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/status.dart';
import 'settings_components.dart';

class ModelSettingsPage extends StatelessWidget {
  const ModelSettingsPage({super.key});
  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final c = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '模型配置',
      subtitle: '使用你自己的模型服务',
      children: [
        const SizedBox(height: 20),
        if (controller.settingsError != null)
          SettingsNotice(
            text: controller.settingsError!,
            icon: Icons.info_outline,
          ),
        if (controller.native.models.isEmpty)
          const EmptyView(
            icon: Icons.auto_awesome_outlined,
            title: '还没有配置模型',
            body: '接入模型服务后，Maeve 才能思考和执行任务。',
          ),
        if (controller.native.models.isNotEmpty)
          SettingsSection(
            title: '已配置的模型',
            rows: [
              for (final model in controller.native.models)
                SettingsRow(
                  title: model.model,
                  subtitle: model.name.isEmpty
                      ? Uri.tryParse(model.baseUrl)?.host
                      : model.name,
                  icon: Icons.auto_awesome_outlined,
                  trailing: controller.native.currentModelId == model.id
                      ? Icon(Icons.check_rounded, color: c.accent, size: 20)
                      : null,
                  onTap: () async {
                    try {
                      await controller.selectModel(model.id);
                    } catch (_) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('模型切换失败，请重试')),
                        );
                      }
                    }
                  },
                ),
            ],
          ),
        const SizedBox(height: 24),
        FilledButton.icon(
          onPressed: () => AppPage.open(context, const AddModelPage()),
          icon: const Icon(Icons.add_rounded, size: 18),
          label: const Text('添加模型接入'),
        ),
        const SizedBox(height: 14),
        Text(
          '模型负责思考，Maeve 负责在设备上行动。密钥由本机安全存储保管。',
          style: MaeveType.caption.copyWith(color: c.ink2, height: 1.6),
        ),
      ],
    );
  }
}

class AddModelPage extends StatefulWidget {
  const AddModelPage({super.key});
  @override
  State<AddModelPage> createState() => _AddModelPageState();
}

class _AddModelPageState extends State<AddModelPage> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController(),
      _url = TextEditingController(),
      _model = TextEditingController(),
      _key = TextEditingController();
  String _kind = 'OpenAiCompat';
  String? _presetId;
  String? _error;
  bool _saving = false;

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

  @override
  void dispose() {
    for (final c in [_name, _url, _model, _key]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate() || _saving) return;
    final controller = MaeveScope.read(context);
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await controller.saveModel({
        'name': _name.text.trim(),
        'baseUrl': _url.text.trim(),
        'model': _model.text.trim(),
        'apiKey': _key.text,
        'kind': _kind,
      });
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
    final presets = MaeveScope.of(context).native.presets;
    return AppPage.list(
      title: '添加模型',
      subtitle: '保存后作为当前主模型使用',
      children: [
        const SizedBox(height: 24),
        Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (presets.isNotEmpty) ...[
                DropdownButtonFormField<String>(
                  key: const ValueKey('provider-preset'),
                  initialValue: _presetId,
                  isExpanded: true,
                  decoration: _decoration(context, '服务商预设'),
                  hint: const Text(
                    '选择服务商，或自定义接入',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  items: [
                    for (final preset in presets)
                      DropdownMenuItem(
                        value: preset.id,
                        child: Text(preset.label),
                      ),
                  ],
                  onChanged: _saving
                      ? null
                      : (id) {
                          final preset = presets.firstWhere(
                            (preset) => preset.id == id,
                          );
                          setState(() {
                            _presetId = id;
                            _kind = preset.kind;
                            _url.text = preset.baseUrl;
                            _model.clear();
                            _key.clear();
                            _error = null;
                            _name.text = preset.baseUrl.isEmpty
                                ? ''
                                : preset.label;
                          });
                        },
                ),
                const SizedBox(height: 20),
              ],
              TextFormField(
                controller: _name,
                decoration: _decoration(context, '接入名称（可选）', '便于你辨认这套配置'),
              ),
              const SizedBox(height: 20),
              DropdownButtonFormField<String>(
                key: ValueKey('model-protocol-$_kind'),
                initialValue: _kind,
                isExpanded: true,
                decoration: _decoration(context, '接口协议'),
                items: const [
                  DropdownMenuItem(
                    value: 'OpenAiCompat',
                    child: Text('OpenAI 兼容'),
                  ),
                  DropdownMenuItem(
                    value: 'OpenAiResponses',
                    child: Text('OpenAI Responses'),
                  ),
                  DropdownMenuItem(
                    value: 'Anthropic',
                    child: Text('Anthropic'),
                  ),
                  DropdownMenuItem(value: 'Gemini', child: Text('Gemini')),
                ],
                onChanged: _saving
                    ? null
                    : (value) => setState(() => _kind = value!),
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: _url,
                key: const ValueKey('model-base-url'),
                keyboardType: TextInputType.url,
                autocorrect: false,
                decoration: _decoration(context, 'API 地址', 'https://…'),
                validator: (value) {
                  final u = Uri.tryParse(value?.trim() ?? '');
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
                controller: _model,
                key: const ValueKey('model-name'),
                autocorrect: false,
                decoration: _decoration(context, '模型名称', '服务商提供的模型 ID'),
                validator: (value) =>
                    value?.trim().isNotEmpty == true ? null : '请填写模型名称',
              ),
              const SizedBox(height: 20),
              TextFormField(
                controller: _key,
                key: const ValueKey('model-api-key'),
                obscureText: true,
                enableSuggestions: false,
                autocorrect: false,
                decoration: _decoration(context, 'API Key', '无需密钥的本地服务可留空'),
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
                '保存不会发送测试请求。地址、密钥和模型是否可用，以实际任务返回的结果为准。',
                style: MaeveType.caption.copyWith(color: c.ink2, height: 1.6),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
