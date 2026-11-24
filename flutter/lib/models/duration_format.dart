/// 执行用时沿用原有的秒、分秒、小时分秒显示。
String durationLabel(int? ms, {String? fallback}) {
  if (ms == null || ms < 0) return fallback ?? '';
  if (ms < 1000) return '${(ms / 1000).toStringAsFixed(1)} 秒';
  final seconds = (ms / 1000).round();
  if (seconds < 60) return '$seconds 秒';
  if (seconds < 3600) return '${seconds ~/ 60} 分 ${seconds % 60} 秒';
  return '${seconds ~/ 3600} 小时 ${seconds ~/ 60 % 60} 分 ${seconds % 60} 秒';
}
