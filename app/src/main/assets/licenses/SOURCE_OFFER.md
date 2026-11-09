# Written offer for complete corresponding source

This offer accompanies the GPL / LGPL / MPL components that are bundled inside the Maeve
APK as **independent child processes** (`libproot.so`, `libmaeverun.so` and the
Alpine-derived `rootfs.tar.xz`). It is provided under GPL-2.0 §3(b), GPL-3.0 §6(b) and
LGPL-3.0 §6 / §4.

## Offer

For a period of **no less than three (3) years** from the distribution date of any Maeve
binary that contains these components, the distributor offers to give any third party a
complete machine-readable copy of the corresponding source code for every GPL / LGPL / MPL
component in that binary, on a medium customarily used for software interchange, at no
charge beyond the actual cost of distribution.

## How to obtain the source

1. **Primary channel (no request needed):** clone this repository at the exact commit used
   to build the APK.

   ```bash
   git clone https://github.com/Daiyimo/Maeve.git
   cd Maeve
   git checkout <commit-sha-shown-in-About-Build-Number>
   ```

2. **Reproducible manifest:** `tools/sandbox/sources.lock` pins every package and version
   that goes into `rootfs.tar.xz`. Rebuild with:

   ```bash
   python tools/sandbox/build-runtime.py --check
   tools/sandbox/build-native.sh
   ```

3. **Written request:** if network access or the repository is unavailable, use the private
   vulnerability/contact channel on the GitHub profile (see `SECURITY.md`) and quote the
   build's commit hash. A physical copy can be arranged at cost.

## What is covered

| Component | Licence | Full text |
|---|---|---|
| PRoot (`libproot.so`) | GPL-2.0-or-later | `licenses/GPL-2.0.txt` |
| talloc / helper libraries (`libmaeverun.so`) | LGPL-3.0 | `licenses/LGPL-3.0.txt` |
| Alpine-derived root filesystem packages | GPL / LGPL / MPL (per package) | `licenses/GPL-2.0.txt`, `licenses/GPL-3.0.txt`, `licenses/LGPL-3.0.txt` |

The licence texts, this offer and a verbatim copy of `NOTICE` are also mirrored into
`app/src/main/assets/licenses/` so that they ship **inside** the APK and are reachable from
the in-app licence screen.

## Note on aggregation

These programs run as separate processes launched by Maeve; they are not linked into the
Maeve application code. Maeve itself remains an Apache-2.0 derivative work of the upstream project. This
document makes no claim about the licence of the main work — see `LICENSE`, `NOTICE` and
`ATTRIBUTION.md`. This is engineering bookkeeping, not legal advice.
