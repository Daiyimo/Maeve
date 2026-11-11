import 'dart:math' as math;

typedef EclipsePoint = ({double x, double y});
typedef EclipseRay = ({EclipsePoint start, EclipsePoint end, double alpha});
typedef EclipseGrain = ({EclipsePoint center, double radius, double alpha});

/// Unit-square geometry shared by the live painter and launcher-vector export.
/// The interior is empty; only the corona and its data edge carry the identity.
abstract final class EclipseGeometry {
  static const radius = .355;
  static const peak = -math.pi / 4;
  static const segments = 240;

  static double envelope(double angle, double center, double width) {
    final distance = math.atan2(
      math.sin(angle - center),
      math.cos(angle - center),
    );
    return math.exp(-distance * distance / (2 * width * width));
  }

  static double thickness(double angle) =>
      .006 + .038 * math.pow((1 + math.cos(angle - peak)) / 2, 1.7);

  static EclipsePoint point(double angle, double radius) =>
      (x: .5 + math.cos(angle) * radius, y: .5 + math.sin(angle) * radius);

  static List<EclipsePoint> corona({double minimum = 0}) => [
    for (var i = 0; i <= segments; i++)
      point(
        i * math.pi * 2 / segments,
        radius + math.max(minimum, thickness(i * math.pi * 2 / segments)),
      ),
    for (var i = segments; i >= 0; i--)
      point(i * math.pi * 2 / segments, radius),
  ];

  static double _noise(int i, int salt) {
    var value = ((i + 1) * 0x9e3779b1 ^ (salt + 17) * 0x85ebca6b) & 0xffffffff;
    value = (value ^ (value << 13)) & 0xffffffff;
    value = (value ^ (value >> 17)) & 0xffffffff;
    value = (value ^ (value << 5)) & 0xffffffff;
    return (value & 0xffffff) / 0x1000000;
  }

  /// A stable fine edge in equilibrium; only localized deviations move at work.
  static List<EclipseRay> rays({
    bool running = false,
    double phase = 0,
    int count = 260,
    double minimum = 0,
  }) => [
    for (var i = 0; i < count; i++) _ray(i, count, running, phase, minimum),
  ];

  static EclipseRay _ray(
    int i,
    int count,
    bool running,
    double phase,
    double minimum,
  ) {
    final angle = i * math.pi * 2 / count;
    final outer = radius + math.max(minimum, thickness(angle));
    final grain = _noise(i, 3);
    final base = .0015 + .008 * grain * grain;
    final wave = .80 + .20 * math.sin(phase * math.pi * 2 + angle * 13);
    final deviation = running
        ? (envelope(angle, peak + .09 * math.sin(phase * math.pi * 2), .13) *
                      .075 +
                  envelope(angle, 1.05, .14) * .034 +
                  envelope(angle, 2.65, .09) * .024) *
              (.35 + .65 * grain) *
              wave
        : 0.0;
    return (
      start: point(angle, outer - .001),
      end: point(angle, outer + base + deviation),
      alpha: .42 + grain * .40,
    );
  }

  static List<EclipseGrain> grains() => [
    for (var i = 0; i < 48; i++)
      (
        center: point(
          _noise(i, 8) * math.pi * 2,
          radius +
              thickness(_noise(i, 8) * math.pi * 2) +
              .013 +
              _noise(i, 9) * .023,
        ),
        radius: .0013 + _noise(i, 10) * .0013,
        alpha: .25 + _noise(i, 11) * .38,
      ),
  ];
}
