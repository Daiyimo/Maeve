#!/usr/bin/env python3
"""Generate the Maeve adaptive-icon foreground (M2 first cut).

Motif per docs/spec/visual-tokens.md §4: two offset rings (outer = main
screen, inner = hidden display) plus one off-centre light point (the step
currently running). Only the glyph is drawn, on a transparent background; the
foggy-violet gradient lives in res/drawable/maeve_launcher_background.xml.

All geometry stays inside the 72dp visible area of the 108dp adaptive grid.

Requires numpy for supersampling; the resulting PNG is committed, so CI never
runs this script. No dsh-Go source, asset, font or colour literal is copied —
the values below are derived from the Maeve design contract.

Usage:
    python3 tools/maeve/make_launcher_art.py
"""

from __future__ import annotations

import pathlib
import struct
import zlib

import numpy as np

OUT = (
    pathlib.Path(__file__).resolve().parents[2]
    / "app/src/main/res/drawable-nodpi/maeve_launcher_art.png"
)
SIZE = 512
SS = 3
N = SIZE * SS

yy, xx = np.mgrid[0:N, 0:N].astype(np.float64)
x = xx / SS + 0.5 / SS
y = yy / SS + 0.5 / SS


def ring(cx: float, cy: float, r: float, w: float, rgb: tuple[int, int, int]):
    d = np.hypot(x - cx, y - cy)
    outer = np.clip((r + w / 2 - d) + 0.5, 0.0, 1.0)
    inner = np.clip((d - (r - w / 2)) + 0.5, 0.0, 1.0)
    return np.array(rgb, dtype=np.float64), np.minimum(outer, inner)


def dot(cx: float, cy: float, r: float, rgb: tuple[int, int, int]):
    d = np.hypot(x - cx, y - cy)
    return np.array(rgb, dtype=np.float64), np.clip((r - d) + 0.5, 0.0, 1.0)


layers = [
    # outer ring — the screen you keep using
    ring(238, 240, 118, 15, (0xB9, 0xAD, 0xD8)),
    # inner ring — the hidden display, offset down-right
    ring(274, 272, 74, 15, (0xE3, 0xDB, 0xF5)),
    # light point — the step running right now
    dot(321, 157, 16, (0xFF, 0xFF, 0xFF)),
]

premul = np.zeros((N, N, 3), dtype=np.float64)
alpha = np.zeros((N, N), dtype=np.float64)
for colour, a in layers:
    premul = premul * (1 - a[..., None]) + colour * a[..., None]
    alpha = a + alpha * (1 - a)

straight = premul / np.maximum(alpha, 1e-6)[..., None]
foreground = np.concatenate([straight, alpha[..., None] * 255.0], axis=2)

# In-app logo: the same glyph on the reverie gradient, opaque and square.
t = np.clip((x + y) / (2.0 * SIZE), 0.0, 1.0)[..., None]
start = np.array([0x6E, 0x62, 0x96], dtype=np.float64)
end = np.array([0x33, 0x2B, 0x4A], dtype=np.float64)
bg = start * (1 - t) + end * t
logo_rgb = straight * alpha[..., None] + bg * (1 - alpha[..., None])
logo = np.concatenate([logo_rgb, np.full((N, N, 1), 255.0)], axis=2)


def downsample(arr: np.ndarray) -> np.ndarray:
    small = arr.reshape(SIZE, SS, SIZE, SS, 4).mean(axis=(1, 3))
    return np.clip(np.rint(small), 0, 255).astype(np.uint8)


def chunk(tag: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + tag
        + data
        + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    )


def write_png(path: pathlib.Path, arr: np.ndarray) -> None:
    rgba8 = downsample(arr)
    raw = b"".join(b"\x00" + rgba8[i].tobytes() for i in range(SIZE))
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path.as_posix()} ({len(png)} bytes, {SIZE}x{SIZE} RGBA)")


write_png(OUT, foreground)
write_png(OUT.with_name("maeve_logo.png"), logo)
