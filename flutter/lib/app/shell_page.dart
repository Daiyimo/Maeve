import 'package:flutter/material.dart';

import 'chat_page.dart';
import '../design/maeve_type.dart';

/// 抽屉打开时淡出底层页面，让半透明玻璃只透出背景，不透出首页徽标。
class ShellPage extends StatelessWidget {
  const ShellPage(this.drawer, this.onMenu, {super.key});

  final double drawer;
  final VoidCallback onMenu;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= MaeveBreakpoints.wide;
    return Opacity(
      opacity: wide ? 1 : 1 - drawer,
      child: ChatPage(onMenu: onMenu),
    );
  }
}
