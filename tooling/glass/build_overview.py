#!/usr/bin/env python3
"""Build the Maeve liquid-glass comparison board from emulator captures."""
from __future__ import annotations

import argparse
import base64
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

PANELS = [
    ("flutter-chat", "Flutter", "聊天页 · 顶栏 + 输入栏", "CHROME"),
    ("flutter-drawer", "Flutter", "聊天抽屉 · 真实背景折射", "SHEET"),
    ("flutter-sheet", "Flutter", "消息面板 ·  Sheet 折射", "SHEET"),
    ("compose-top", "Compose", "原生顶栏 · 实时内容采样", "CHROME"),
    ("compose-composer", "Compose", "原生输入栏 · 低磨砂透镜", "CHROME"),
    ("compose-sheet", "Compose", "原生面板 · 边缘色散", "SHEET"),
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


def extract_flutter(log: Path) -> dict[str, Image.Image]:
    images: dict[str, Image.Image] = {}
    pattern = re.compile(r"GLASS_SCREENSHOT:([^:]+):([A-Za-z0-9+/=]+)")
    for name, payload in pattern.findall(log.read_text(encoding="utf-8", errors="ignore")):
        from io import BytesIO

        images[name] = Image.open(BytesIO(base64.b64decode(payload))).convert("RGB")
    required = {"flutter-chat", "flutter-drawer", "flutter-sheet"}
    missing = required - images.keys()
    if missing:
        raise SystemExit(f"Flutter log missing screenshots: {sorted(missing)}")
    return images


def rounded_paste(canvas: Image.Image, image: Image.Image, box: tuple[int, int, int, int], radius: int) -> None:
    x0, y0, x1, y1 = box
    fitted = ImageOps.contain(image, (x1 - x0, y1 - y0), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
    mask = Image.new("L", fitted.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, fitted.width - 1, fitted.height - 1), radius=radius, fill=255)
    layer.paste(fitted, (0, 0), mask)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (x0 + 2, y0 + 12, x1 + 2, y1 + 12), radius=radius, fill=(0, 0, 0, 120)
    )
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(16)))
    canvas.alpha_composite(layer, (x0, y0))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--flutter-log", type=Path, required=True)
    parser.add_argument("--compose-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    images = extract_flutter(args.flutter_log)
    for name in ("compose-top", "compose-composer", "compose-sheet"):
        path = args.compose_dir / f"{name}.png"
        if not path.exists():
            raise SystemExit(f"Missing Compose capture: {path}")
        images[name] = Image.open(path).convert("RGB")

    margin, gap = 48, 24
    panel_w, panel_h = 412, 915
    label_h = 66
    header_h, footer_h = 150, 86
    width = margin * 2 + panel_w * 3 + gap * 2
    height = header_h + (label_h + panel_h) * 2 + gap + footer_h
    canvas = Image.new("RGBA", (width, height), "#0A0C12")
    draw = ImageDraw.Draw(canvas)

    # Quiet aurora backdrop keeps the board in the same visual family as the app.
    glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-260, -320, 720, 660), fill=(104, 82, 255, 46))
    gd.ellipse((width - 640, 120, width + 260, 1020), fill=(36, 174, 255, 34))
    gd.ellipse((260, height - 720, 1080, height + 120), fill=(255, 106, 170, 24))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(120)))
    draw = ImageDraw.Draw(canvas)

    draw.text((margin, 34), "Maeve · 液态玻璃折射对比", font=font(38, True), fill="#F7F8FC")
    draw.text(
        (margin, 88),
        "同一光学契约 · Android API 36 模拟器实拍 · 中心保细节 / 边缘强折射 / 轻微色散",
        font=font(18),
        fill="#AEB6C8",
    )
    draw.rounded_rectangle((width - 286, 42, width - margin, 82), radius=20, fill="#171B27", outline="#2A3245")
    draw.text((width - 266, 50), "σ 3 / 4   ·   REFRACT 14 / 18dp", font=font(14, True), fill="#BFD0FF")

    for index, (name, platform, title, tier) in enumerate(PANELS):
        row, col = divmod(index, 3)
        x = margin + col * (panel_w + gap)
        y = header_h + row * (label_h + panel_h + gap)
        accent = "#8CA8FF" if platform == "Flutter" else "#6FE3C1"
        draw.rounded_rectangle((x, y + 8, x + 78, y + 36), radius=14, fill="#171B27")
        draw.text((x + 12, y + 12), platform, font=font(14, True), fill=accent)
        draw.text((x + 92, y + 8), title, font=font(19, True), fill="#F2F4FA")
        draw.text((x + 92, y + 35), tier, font=font(12), fill="#7F8AA3")
        rounded_paste(canvas, images[name], (x, y + label_h, x + panel_w, y + label_h + panel_h), 26)
        draw.rounded_rectangle((x, y + label_h, x + panel_w, y + label_h + panel_h), radius=26, outline="#2B3346", width=2)

    draw.line((margin, height - footer_h + 10, width - margin, height - footer_h + 10), fill="#242B3B", width=2)
    draw.text(
        (margin, height - footer_h + 28),
        "顶栏与输入栏使用 Chrome 档；抽屉与面板使用 Sheet 档。Flutter 与 Compose 的参数由 shared/glass_tokens.json 锁定。",
        font=font(16),
        fill="#AEB6C8",
    )
    draw.text(
        (margin, height - footer_h + 56),
        "回退：Impeller / API 33+ 折射；Skia / API 31–32 低磨砂；关模糊或高对比度使用不透明复合底。",
        font=font(14),
        fill="#738098",
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(args.output, quality=95)
    print(args.output)


if __name__ == "__main__":
    main()
