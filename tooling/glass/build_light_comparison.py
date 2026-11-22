#!/usr/bin/env python3
"""Build a light-theme original-vs-refractive Maeve comparison board."""
from __future__ import annotations

import argparse
import base64
import re
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

PANELS = [
    ("chat", "聊天页 · 顶栏与输入栏"),
    ("drawer", "聊天抽屉"),
    ("sheet", "消息操作面板"),
    ("settings", "设置首页"),
    ("settings-glass", "设置 · 液态玻璃浓度"),
    ("appearance", "外观与配色"),
    ("model", "模型配置"),
    ("about", "关于 Maeve"),
    ("history", "执行记录"),
    ("welcome", "新手引导"),
]


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        Path(r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\segoeui.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def extract(log: Path, variant: str, out_dir: Path) -> dict[str, Image.Image]:
    pattern = re.compile(r"GLASS_SCREENSHOT:" + re.escape(variant) + r"-([^:]+):([A-Za-z0-9+/=]+)")
    images: dict[str, Image.Image] = {}
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, payload in pattern.findall(log.read_text(encoding="utf-8", errors="ignore")):
        image = Image.open(BytesIO(base64.b64decode(payload))).convert("RGB")
        # The integration surface includes black Android system-bar strips.
        # Crop them so the light-theme board focuses on Maeve UI.
        image = image.crop((0, 28, image.width, image.height - 24))
        images[name] = image
        image.save(out_dir / f"{name}.png")
    required = {name for name, _ in PANELS}
    missing = required - images.keys()
    if missing:
        raise SystemExit(f"{variant} log missing screenshots: {sorted(missing)}")
    return images


def paste_panel(canvas: Image.Image, image: Image.Image, x: int, y: int, w: int, h: int) -> None:
    fitted = ImageOps.contain(image, (w, h), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mask = Image.new("L", fitted.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, fitted.width - 1, fitted.height - 1), radius=22, fill=255)
    layer.paste(fitted, ((w - fitted.width) // 2, (h - fitted.height) // 2), mask)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x + 2, y + 8, x + w + 2, y + h + 8), radius=22, fill=(30, 35, 50, 45))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10)))
    canvas.alpha_composite(layer, (x, y))
    ImageDraw.Draw(canvas).rounded_rectangle((x, y, x + w, y + h), radius=22, outline=(205, 210, 221), width=2)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--original-log", type=Path, required=True)
    parser.add_argument("--modified-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    out_dir = args.output.parent
    original = extract(args.original_log, "original", out_dir / "original")
    modified = extract(args.modified_log, "modified", out_dir / "modified")

    margin, gap, label_h = 36, 18, 48
    panel_w, panel_h = 412, 863
    header_h, footer_h = 170, 74
    width = margin * 2 + panel_w * 2 + gap
    height = header_h + len(PANELS) * (label_h + panel_h + gap) + footer_h
    canvas = Image.new("RGBA", (width, height), "#F3F4F8")
    draw = ImageDraw.Draw(canvas)

    draw.text((margin, 30), "Maeve · 浅色主题液态玻璃对比", font=font(34, True), fill="#171A23")
    draw.text((margin, 78), "左：原版磨砂方案　·　右：强折射修改版　·　同一组假数据与页面路径", font=font(17), fill="#626A7D")
    draw.rounded_rectangle((margin, 118, margin + 150, 150), radius=16, fill="#E7E9F0")
    draw.text((margin + 18, 124), "原版", font=font(16, True), fill="#5D6475")
    right_x = margin + panel_w + gap
    draw.rounded_rectangle((right_x, 118, right_x + 150, 150), radius=16, fill="#DDE7FF")
    draw.text((right_x + 18, 124), "强折射版", font=font(16, True), fill="#3559B8")

    for index, (name, title) in enumerate(PANELS):
        y = header_h + index * (label_h + panel_h + gap)
        draw.text((margin, y + 10), f"{index + 1:02d}", font=font(18, True), fill="#8B93A5")
        draw.text((margin + 44, y + 8), title, font=font(21, True), fill="#242936")
        draw.line((margin, y + label_h - 8, width - margin, y + label_h - 8), fill="#D9DDE7", width=1)
        paste_panel(canvas, original[name], margin, y + label_h, panel_w, panel_h)
        paste_panel(canvas, modified[name], margin + panel_w + gap, y + label_h, panel_w, panel_h)

    draw.line((margin, height - footer_h + 12, width - margin, height - footer_h + 12), fill="#D9DDE7", width=2)
    draw.text((margin, height - footer_h + 30), "修改重点：顶栏恢复采样 · 输入栏移除渐隐 · 抽屉改为 Sheet 折射 · 双端参数统一", font=font(15), fill="#626A7D")
    draw.text((margin, height - footer_h + 52), "截图环境：Android API 36 模拟器 / Flutter Impeller；未在真机安装。", font=font(13), fill="#8B93A5")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(args.output, quality=95)
    print(args.output)


if __name__ == "__main__":
    main()
