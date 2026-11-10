import 'package:flutter/material.dart';

import 'app/maeve_app.dart';

export 'app/maeve_app.dart';
export 'app/maeve_controller.dart';
export 'bridge/maeve_bridge.dart';

/// 入口只做一件事:装进 [MaeveApp]。视觉与状态都在 `lib/app/` 与 `lib/ui/`。
void main() => runApp(const MaeveApp());
