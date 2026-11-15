"""Compare the Flutter payload in an APK with the selected locally built AAR."""
import argparse
import hashlib
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--mode", choices=("debug", "profile", "release"), required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
repo = root / "flutter/build" / ("host" if args.mode == "debug" else f"host-{args.mode}/host") / "outputs/repo"
aar = repo / f"com/daiyimo/maeve_flutter/flutter_{args.mode}/1.0/flutter_{args.mode}-1.0.aar"
with zipfile.ZipFile(aar) as source, zipfile.ZipFile(args.apk) as target:
    names = set(target.namelist())
    kernel = "assets/flutter_assets/kernel_blob.bin"
    if (kernel in names) != (args.mode == "debug"):
        raise SystemExit("Flutter kernel presence does not match the selected mode")
    compared = 0
    for name in source.namelist():
        if name.endswith("/"):
            continue
        if name.startswith("assets/flutter_assets/"):
            destination = name
        elif name.startswith("jni/") and name.endswith("/libapp.so"):
            destination = "lib/" + name.removeprefix("jni/")
            if destination not in names:  # ABI split APKs omit other architectures.
                continue
        else:
            continue
        if destination not in names or source.read(name) != target.read(destination):
            raise SystemExit(f"Flutter payload mismatch: {destination}")
        compared += 1
    if not compared:
        raise SystemExit("No matching Flutter payload was found")
print(f"Verified {args.mode} Flutter payload: {compared} entries")
print("APK SHA-256: " + hashlib.sha256(args.apk.read_bytes()).hexdigest())
