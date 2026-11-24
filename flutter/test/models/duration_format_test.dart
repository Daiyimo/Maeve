import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/models/duration_format.dart';

void main() {
  test('execution duration restores seconds and minutes', () {
    expect(durationLabel(97599), '1 分 38 秒');
    expect(durationLabel(12000), '12 秒');
    expect(durationLabel(500), '0.5 秒');
    expect(durationLabel(3725000), '1 小时 2 分 5 秒');
    expect(durationLabel(null, fallback: '12 秒'), '12 秒');
  });
}
