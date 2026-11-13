class TokenUsage {
  const TokenUsage({
    this.input = 0,
    this.output = 0,
    this.cached = 0,
    this.calls = 0,
    this.unreported = 0,
    this.reported = true,
    this.scope = '',
  });
  final int input, output, cached, calls, unreported;
  final bool reported;
  final String scope;
  int get total => input + output;
  factory TokenUsage.fromMap(Map map) {
    int n(String k) => ((map[k] as num?)?.toInt() ?? 0).clamp(0, 1 << 62);
    return TokenUsage(
      input: n('input'),
      output: n('output'),
      cached: n('cached'),
      calls: n('calls'),
      unreported: n('unreported'),
      reported:
          map['reported'] as bool? ??
          (n('calls') == 0 || n('calls') > n('unreported')),
      scope: map['scope'] as String? ?? '',
    );
  }
}

String tokenNumber(int value) => value.toString().replaceAllMapped(
  RegExp(r'\B(?=(\d{3})+(?!\d))'),
  (_) => ',',
);
