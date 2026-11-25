import 'package:flutter/material.dart';

/// STEPX Neo (PZ261A) chassis shared by the phone stage and the share image.
class StepxNeoFrame extends StatelessWidget {
  const StepxNeoFrame({
    super.key,
    required this.viewport,
    required this.child,
    this.width = 351,
  });

  static const portraitViewport = Size(1200, 2670);

  static const _rimFraction = 5.5 / 351;
  final Size viewport;
  final Widget child;
  final double width;

  @override
  Widget build(BuildContext context) {
    final rim = width * _rimFraction;
    final height = (width - rim * 2) / viewport.aspectRatio + rim * 2;
    final portrait = viewport.height > viewport.width;
    return SizedBox(
      width: width,
      height: height,
      child: Stack(
        clipBehavior: Clip.none,
        fit: StackFit.expand,
        children: [
          if (portrait) ...[
            // PZ261A side-profile photo: the rocker, power, and AI key.
            _sideKey(
              'stepx-neo-volume-rocker',
              height * .13,
              height * .12,
              rocker: true,
            ),
            _sideKey('stepx-neo-power-key', height * .285, height * .077),
            _sideKey(
              'stepx-neo-ai-key',
              height * .387,
              height * .083,
              orange: true,
            ),
          ],
          Container(
            key: const ValueKey('stepx-neo-phone-body'),
            padding: EdgeInsets.all(rim),
            decoration: BoxDecoration(
              color: const Color(0xff211c27),
              borderRadius: BorderRadius.circular(width * (28 / 351)),
              border: Border.all(
                color: const Color(0xff504758),
                width: .5,
                strokeAlign: BorderSide.strokeAlignInside,
              ),
              boxShadow: const [
                BoxShadow(
                  color: Color(0x402d1838),
                  offset: Offset(0, 11),
                  blurRadius: 21,
                  spreadRadius: -9,
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(width * (22.5 / 351)),
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final lens = constraints.maxWidth * .043;
                  return Stack(
                    fit: StackFit.expand,
                    children: [
                      child,
                      if (portrait)
                        Positioned(
                          // The PZ261A cutout is centered at y=72 on its
                          // 2670-pixel screen. The visible lens is smaller.
                          top: constraints.maxHeight * (72 / 2670) - lens / 2,
                          left: (constraints.maxWidth - lens) / 2,
                          width: lens,
                          height: lens,
                          child: const DecoratedBox(
                            key: ValueKey('stepx-neo-front-camera'),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: RadialGradient(
                                center: Alignment(-.24, -.26),
                                colors: [
                                  Color(0xff393141),
                                  Color(0xff100d14),
                                  Color(0xff08080a),
                                ],
                                stops: [0, .29, 1],
                              ),
                              border: Border.fromBorderSide(
                                BorderSide(
                                  color: Color(0xff221d28),
                                  width: .65,
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _sideKey(
    String key,
    double top,
    double height, {
    bool rocker = false,
    bool orange = false,
  }) => Positioned(
    right: -width * (4.5 / 351),
    top: top,
    width: width * (7 / 351),
    height: height,
    child: DecoratedBox(
      key: ValueKey(key),
      decoration: BoxDecoration(
        borderRadius: const BorderRadius.horizontal(right: Radius.circular(2)),
        gradient: LinearGradient(
          colors: orange
              ? const [Color(0xff963619), Color(0xffff783a), Color(0xffd85025)]
              : const [Color(0xff1b161e), Color(0xff625766), Color(0xff372e3b)],
        ),
      ),
      child: rocker
          ? const Center(
              child: ColoredBox(
                color: Color(0xff3c3340),
                child: SizedBox(height: .55, width: double.infinity),
              ),
            )
          : null,
    ),
  );
}
