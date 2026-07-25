#!/usr/bin/env python3
"""
Generate high-resolution static map images from the Mapbox Static Images API.

The API caps a single request at 1280x1280 logical pixels, so this script
stitches a grid of tiles together to reach any target resolution, keeping the
exact center + zoom you configure.

Two levers control how big roads/labels appear ("content zoom"):
  * ZOOM  - geographic zoom. Higher = smaller area, everything bigger.
  * SCALE - 1 or 2. 2 renders the map at @2x (retina): labels and icons are
            drawn ~2x larger relative to the map, same geographic area.
            This is usually what you want for crisp, readable text.

Tip for cropping later: generate a MASTER image larger than 4K (e.g. 5760x3240)
with SCALE = 2, then crop the region you like down to 1920x1080 (Full HD) or
3840x2160 (4K). Because the master is oversized and @2x, both crops stay sharp.

Usage:
  python3 generate_map_images.py                 # uses the config below
  python3 generate_map_images.py --zoom 12 --scale 2 --width 5760 --height 3240
  python3 generate_map_images.py --lat 51.05 --lon 13.73 --out-dir ../src/lib/assets
"""

import argparse
import math
import os
from pathlib import Path

import requests
from PIL import Image

# --------------------------------------------------------------------------- #
# CONFIG - edit these defaults, or override any of them via CLI flags.
# --------------------------------------------------------------------------- #
CENTER_LAT = 51.0504
CENTER_LON = 13.7373
ZOOM = 11              # geographic zoom level. Bump this for bigger content.
SCALE = 2             # 1 = normal, 2 = @2x retina (bigger, sharper text)
WIDTH = 3840          # final image width  in px (matches 4K; used as a CSS
HEIGHT = 2160         # final image height in px  object-cover placeholder)
BEARING = 0           # map rotation in degrees

# One entry per image to generate: (output filename, mapbox style id)
# NOTE: the "mapbox/standard" style is NOT supported by the Static Images API.
STYLES = [
    ("map-light.png", "mapbox/streets-v12"),
    ("map-dark.png", "mapbox/traffic-night-v2"),
]

# Where to write the images (relative to this script's directory by default).
OUT_DIR = "../src/lib/assets"

# --------------------------------------------------------------------------- #

SCRIPT_DIR = Path(__file__).resolve().parent
MAX_TILE = 1280           # Static Images API per-request limit (logical px)
TILE_SIZE = 512           # Mapbox uses 512px world tiles


def load_token() -> str:
    """Read PUBLIC_MAPBOX_TOKEN from env or the web/.env file."""
    token = os.environ.get("PUBLIC_MAPBOX_TOKEN")
    if token:
        return token
    env_path = SCRIPT_DIR / ".." / ".env"
    if env_path.exists():
        for line in env_path.read_text().splitlines():
            line = line.strip()
            if line.startswith("PUBLIC_MAPBOX_TOKEN="):
                return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit(
        "No Mapbox token found. Set PUBLIC_MAPBOX_TOKEN or add it to web/.env"
    )


def lon_to_px(lon: float, world: float) -> float:
    return (lon + 180.0) / 360.0 * world


def lat_to_py(lat: float, world: float) -> float:
    s = math.sin(math.radians(lat))
    return (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * world


def px_to_lon(px: float, world: float) -> float:
    return px / world * 360.0 - 180.0


def py_to_lat(py: float, world: float) -> float:
    n = math.pi - 2 * math.pi * py / world
    return math.degrees(math.atan(math.sinh(n)))


def split(total: int, max_size: int) -> list[tuple[int, int]]:
    """Split `total` logical px into consecutive [start, end) chunks <= max_size."""
    count = math.ceil(total / max_size)
    bounds = [round(k * total / count) for k in range(count + 1)]
    return [(bounds[k], bounds[k + 1]) for k in range(count)]


def fetch_tile(token, style, lon, lat, zoom, bearing, w, h, scale):
    ratio = "@2x" if scale == 2 else ""
    url = (
        f"https://api.mapbox.com/styles/v1/{style}/static/"
        f"{lon},{lat},{zoom},{bearing}/{w}x{h}{ratio}"
        f"?access_token={token}&attribution=false&logo=false"
    )
    r = requests.get(url, timeout=60)
    if not r.ok:
        raise SystemExit(f"Tile request failed ({r.status_code}) for {style}: {r.text}")
    from io import BytesIO
    return Image.open(BytesIO(r.content)).convert("RGB")


def generate(token, style, out_path, lat, lon, zoom, bearing, width, height, scale):
    if scale not in (1, 2):
        raise SystemExit("SCALE must be 1 or 2 (the Static API only supports @2x).")

    world = TILE_SIZE * (2 ** zoom)          # world size in logical px at this zoom
    logical_w = width // scale               # geographic coverage in logical px
    logical_h = height // scale
    actual_w = logical_w * scale
    actual_h = logical_h * scale

    center_px = lon_to_px(lon, world)
    center_py = lat_to_py(lat, world)
    top_left_px = center_px - logical_w / 2
    top_left_py = center_py - logical_h / 2

    cols = split(logical_w, MAX_TILE)
    rows = split(logical_h, MAX_TILE)

    canvas = Image.new("RGB", (actual_w, actual_h))
    total = len(cols) * len(rows)
    i = 0
    for (y0, y1) in rows:
        for (x0, x1) in cols:
            i += 1
            seg_w, seg_h = x1 - x0, y1 - y0
            cx = top_left_px + (x0 + x1) / 2
            cy = top_left_py + (y0 + y1) / 2
            tlon = px_to_lon(cx, world)
            tlat = py_to_lat(cy, world)
            tile = fetch_tile(token, style, tlon, tlat, zoom, bearing, seg_w, seg_h, scale)
            canvas.paste(tile, (x0 * scale, y0 * scale))
            print(f"  {out_path.name}: tile {i}/{total}")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out_path)
    print(f"wrote {out_path} ({actual_w}x{actual_h}, {out_path.stat().st_size // 1024} KB)")


def main():
    p = argparse.ArgumentParser(description="Generate stitched high-res Mapbox images.")
    p.add_argument("--lat", type=float, default=CENTER_LAT)
    p.add_argument("--lon", type=float, default=CENTER_LON)
    p.add_argument("--zoom", type=float, default=ZOOM)
    p.add_argument("--scale", type=int, default=SCALE, choices=(1, 2))
    p.add_argument("--width", type=int, default=WIDTH)
    p.add_argument("--height", type=int, default=HEIGHT)
    p.add_argument("--bearing", type=float, default=BEARING)
    p.add_argument("--out-dir", default=OUT_DIR)
    args = p.parse_args()

    token = load_token()
    out_dir = (SCRIPT_DIR / args.out_dir).resolve()

    for name, style in STYLES:
        print(f"Generating {name} [{style}] ...")
        generate(
            token, style, out_dir / name,
            args.lat, args.lon, args.zoom, args.bearing,
            args.width, args.height, args.scale,
        )


if __name__ == "__main__":
    main()
