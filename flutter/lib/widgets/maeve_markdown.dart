import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';

import '../bridge/maeve_bridge.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

/// Answer typography shared by the conversation and recorded model replies.
class MaeveMarkdown extends StatelessWidget {
  const MaeveMarkdown({super.key, required this.data, this.style});
  final String data;
  final TextStyle? style;

  Future<void> _open(BuildContext context, String? href) async {
    final uri = Uri.tryParse(href ?? '');
    if (uri == null ||
        !{'http', 'https'}.contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      return;
    }
    var opened = false;
    try {
      opened = await MaeveBridge.openUrl(uri.toString());
    } catch (_) {
      /* Report a failed user action below. */
    }
    if (!opened && context.mounted) {
      ScaffoldMessenger.maybeOf(context)
          ?.showSnackBar(const SnackBar(content: Text('链接暂时无法打开')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final body = (style ?? MaeveType.script).copyWith(
      color: style?.color ?? c.ink,
    );
    final heading = body.copyWith(fontWeight: FontWeight.w600, height: 1.4);
    return MediaQuery.removePadding(
      context: context,
      removeTop: true,
      removeBottom: true,
      child: MarkdownBody(
        data: data,
        selectable: false, // The message's long-press copy / quote actions remain available.
        softLineBreak: true,
        onTapLink: (_, href, _) => _open(context, href),
        imageBuilder: (uri, title, alt) => Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: () => _open(context, uri.toString()),
            icon: const Icon(Icons.image_outlined, size: 18),
            label: Text(alt?.isNotEmpty == true ? alt! : '查看图片'),
          ),
        ),
        styleSheet: MarkdownStyleSheet(
          p: body,
          a: body.copyWith(
            color: c.accent,
            decoration: TextDecoration.underline,
          ),
          h1: heading.copyWith(fontSize: 23),
          h2: heading.copyWith(fontSize: 20),
          h3: heading.copyWith(fontSize: 18),
          h4: heading,
          h5: heading,
          h6: heading,
          strong: const TextStyle(fontWeight: FontWeight.w600),
          em: const TextStyle(fontStyle: FontStyle.italic),
          blockSpacing: 12,
          listIndent: 22,
          listBullet: body.copyWith(color: c.ink2),
          blockquote: body.copyWith(color: c.ink2),
          blockquotePadding: const EdgeInsets.symmetric(
            horizontal: 14,
            vertical: 8,
          ),
          blockquoteDecoration: BoxDecoration(
            border: Border(
              left: BorderSide(color: c.accent.withValues(alpha: .4), width: 2),
            ),
          ),
          code: body.copyWith(
            fontFamily: MaeveType.monoFamily,
            fontSize: (body.fontSize ?? 16) - 1,
            backgroundColor: c.fill,
          ),
          codeblockPadding: const EdgeInsets.all(14),
          codeblockDecoration: BoxDecoration(
            color: c.fill,
            borderRadius: BorderRadius.circular(12),
          ),
          tableHead: MaeveType.caption.copyWith(
            color: c.ink,
            fontWeight: FontWeight.w600,
          ),
          tableBody: MaeveType.caption.copyWith(color: c.ink),
          tableColumnWidth: const IntrinsicColumnWidth(),
          tableCellsPadding: const EdgeInsets.symmetric(
            horizontal: 12,
            vertical: 10,
          ),
          tableHeadCellsDecoration: BoxDecoration(color: c.fill),
          tableBorder: TableBorder.all(color: c.rule, width: .5),
          tableScrollbarThumbVisibility: true,
          horizontalRuleDecoration: BoxDecoration(
            border: Border(top: BorderSide(color: c.rule, width: .75)),
          ),
        ),
      ),
    );
  }
}
