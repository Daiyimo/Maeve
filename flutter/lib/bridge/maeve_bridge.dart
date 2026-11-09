import 'package:flutter/services.dart';

class MaeveBridge {
  static const methods = MethodChannel('maeve/ui');
  static const events = EventChannel('maeve/ui/events');
  static Future<void> openSettings() => methods.invokeMethod('open_settings');
  static Stream<dynamic> get status => events.receiveBroadcastStream();
}
