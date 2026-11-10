import 'package:flutter/material.dart';

/// 连续曲率(超椭圆)半径分档。
///
/// 数值与 Compose 侧 `MaeveRadius` 一致:6 / 10 / 14 / 20 / 28。
/// 另有一个 16 的**输入条**专用值:胶囊读作"聊天软件",16 的纸条读作"便签"。
abstract final class MaeveRadius {
  /// 行内块、角标。
  static const xs = 6.0;

  /// 图标块、小按钮、分段控件。
  static const sm = 10.0;

  /// 按钮、输入框、菜单。
  static const md = 14.0;

  /// 卡片、气泡。
  static const lg = 20.0;

  /// 弹层、抽屉。
  static const xl = 28.0;

  /// 输入条:一张纸条,不是胶囊。
  static const composer = 16.0;

  /// 胶囊:半径 = 高一半。
  static const pill = 999.0;
}

/// 全站共用的一组形状。改这里等于改所有卡片和按钮。
///
/// 连续曲率用 Flutter 的 `RoundedSuperellipseBorder`(与 Compose 侧的超椭圆同族:
/// 曲率连续,不是"直线 + 四分之一圆")。裁切与形状必须来自**同一个实例**,
/// 否则模糊边界与填充边界差 1px。
abstract final class MaeveShapes {
  /// 卡片、按钮、图标块:大圆角连续曲率。
  static const card = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.lg)),
  );

  static const button = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.md)),
  );

  static const tile = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.sm)),
  );

  /// 弹层:只有顶两角是大圆角。
  static const sheet = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.vertical(top: Radius.circular(MaeveRadius.xl)),
  );

  static const dialog = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.xl)),
  );

  /// 输入条:16 的纸条。
  static const composer = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.composer)),
  );

  /// 用户消息:右下角是尾角(指向说话的人)。
  static const userBubble = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.only(
      topLeft: Radius.circular(MaeveRadius.lg),
      topRight: Radius.circular(MaeveRadius.lg),
      bottomRight: Radius.circular(MaeveRadius.xs),
      bottomLeft: Radius.circular(MaeveRadius.lg),
    ),
  );

  /// Maeve 的回答:左下角是尾角,和用户气泡对称。
  static const answerBubble = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.only(
      topLeft: Radius.circular(MaeveRadius.lg),
      topRight: Radius.circular(MaeveRadius.lg),
      bottomRight: Radius.circular(MaeveRadius.lg),
      bottomLeft: Radius.circular(MaeveRadius.xs),
    ),
  );

  static const pill = StadiumBorder();

  static RoundedSuperellipseBorder round(double radius) =>
      RoundedSuperellipseBorder(
        borderRadius: BorderRadius.all(Radius.circular(radius)),
      );

  static RoundedSuperellipseBorder outlined(
    Color color, {
    double radius = MaeveRadius.md,
    double width = .5,
  }) => RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(radius)),
    side: BorderSide(color: color, width: width),
  );
}
