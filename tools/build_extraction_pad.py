#!/usr/bin/env python3
"""Build the Chase extraction-pad prefab from a native Hytale Elemental Circle.

The co-op escape rework (all survivors hold the Heartwood platform together) replaces
the old plain 3x3 orange-light Exit pad with a recognizable VANILLA ritual circle:
the Frost "Middle" medallion (a clean symmetric 11x11 flat-topped altar with a dark
basalt + blue-crystal palette that reads as a frozen, blighted platform).

Why a build-time copy (no runtime fetch): vanilla prefab keys do NOT resolve through
the mod's PrefabStore at runtime - ArenaBuilder.load only finds paths the pack itself
ships (Files.exists in loaded packs). hytale-resources is a dev-time mirror. So, like
Corrupted_Well (tools/repaint_kweebec_prefab.py), we copy the geometry into the pack
once and commit the output; the runtime loads KweebecNightmare/Extraction_Pad.

What this does to the source:
  - keeps only SOLID blocks (drops every "Empty" air cell: the additive force=false
    paste skips them anyway, and dropping them shrinks ~1MB -> ~60KB).
  - STRIPS the embedded encounter trigger (Block_Spawner_Block) + the two
    Furniture_Construction_Sign placeholders - we do not want a Zone3 encounter or
    construction signs spawning on the escape pad.
  - drops the source "fluids"/"entities" arrays (no water pools, no NPC markers).
  - re-anchors so the walkable top ring (source y=13) lands on the floor-snapped
    surface: anchorY = TOP_PLANE_Y, anchorX/Z = 0 (the symmetric center). The raised
    inner medallion (source y=14..19) then sticks up ~6 blocks as the altar; the base
    (source y<13) sits buried under the surface.

Output shape is the mod's flat v8 prefab JSON (same as Exit/Corrupted_Well):
  {version, blockIdVersion, anchorX/Y/Z, blocks:[{x,y,z,name}]}

Usage:  python tools/build_extraction_pad.py            (writes the committed pad)
        python tools/build_extraction_pad.py --inspect  (diagnostics only, no write)
"""

from __future__ import annotations

import json
import os
import sys
from collections import Counter, defaultdict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
HYMMO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))
SOURCE = os.path.join(
    HYMMO_ROOT, "hytale-resources", "assets", "Server", "Prefabs", "Monuments",
    "Unique", "Elemental_Circles", "Frost", "Middle", "Tier_1",
    "Unique_Middle_Tier_1_Circles_Frost_Middle_001.prefab.json",
)
OUT = os.path.join(
    SCRIPT_DIR, "..", "src", "main", "resources", "Server", "Prefabs",
    "KweebecNightmare", "Extraction_Pad.prefab.json",
)

# The walkable ring plane in SOURCE coords.
TOP_PLANE_Y = 13

# anchorY = TOP_PLANE_Y + ANCHOR_RAISE. RAISING anchorY (pinning a HIGHER source block to the
# floor-snapped surface) makes the structure sit LOWER in the world, so the platform is recessed into the
# ground (the walkable ring ~ANCHOR_RAISE blocks below the surrounding terrain, the void-portal rising out
# of the sunken circle). Set this to 0 for a flush platform, or negative to make the platform sit proud.
ANCHOR_RAISE = 4

# Block names dropped on copy (no air, no encounter trigger, no construction signs).
DROP_NAMES = {"Empty", "Block_Spawner_Block", "Furniture_Construction_Sign"}

# The central crystal medallion (source y > TOP_PLANE_Y, near the axis) is removed and REPLACED by a
# no-op purple "void portal" arch (see portal_arch). Real vanilla portal-surface blocks
# (Forgotten_Temple_Portal_*, Hub_Portal_*, Portal_Return/Device) all carry a CollisionEnter teleport
# interaction and WOULD eject a player from the round instance, so the arch is built from INERT vanilla
# blocks: a Rock_Basalt_Brick_Smooth frame (matching the platform) around a Rock_Crystal_Purple_Block surface.
PORTAL_FRAME = "Rock_Basalt_Brick_Smooth"
PORTAL_FILL = "Rock_Crystal_Purple_Block"
PORTAL_GLOW = "Build_Lightsource_Purple"


def portal_arch():
    """A decorative, strictly NO-OP purple void-portal arch standing on the platform center (source coords).

    Centered on x=0, z=0 in the X-Y plane (opening faces +/-z, so the party approaching from +z sees it
    face-on), rising from the walkable ring (y=TOP_PLANE_Y). 5 wide x 5 tall: a basalt frame around a
    3x4 glowing purple-crystal surface, with a purple lightsource behind for the void glow."""
    blocks = []
    base = TOP_PLANE_Y + 1  # first row above the walkable ring
    top = base + 4          # lintel row (y=18)
    # Uprights (basalt) at x=-2 and x=+2.
    for y in range(base, top):
        for x in (-2, 2):
            blocks.append({"x": x, "y": y, "z": 0, "name": PORTAL_FRAME})
    # Lintel (basalt) across the top.
    for x in range(-2, 3):
        blocks.append({"x": x, "y": top, "z": 0, "name": PORTAL_FRAME})
    # Portal surface (purple crystal) - the 3-wide x 4-tall inert "void" face.
    for y in range(base, top):
        for x in (-1, 0, 1):
            blocks.append({"x": x, "y": y, "z": 0, "name": PORTAL_FILL})
    # A purple lightsource one block behind the surface so it glows from within (z=-1, hidden core).
    for y in range(base, top):
        blocks.append({"x": 0, "y": y, "z": -1, "name": PORTAL_GLOW})
    return blocks


def is_central_medallion(b):
    """The raised central crystal spike (and the arch footprint) we clear before splicing the portal."""
    return b["y"] > TOP_PLANE_Y and abs(b["x"]) <= 2 and abs(b["z"]) <= 2


def main() -> int:
    inspect = "--inspect" in sys.argv
    if not os.path.isfile(SOURCE):
        print(f"SOURCE not found: {SOURCE}", file=sys.stderr)
        return 2
    with open(SOURCE, "r", encoding="utf-8") as f:
        src = json.load(f)

    blocks = src.get("blocks", [])
    print(f"source keys: {sorted(src.keys())}")
    print(f"version={src.get('version')} blockIdVersion={src.get('blockIdVersion')} "
          f"anchor=({src.get('anchorX')},{src.get('anchorY')},{src.get('anchorZ')})")
    print(f"total block entries: {len(blocks)}")
    name_counts = Counter(b["name"] for b in blocks)
    print(f"distinct names: {len(name_counts)}")
    for name, n in name_counts.most_common():
        flag = "  <-- DROP" if name in DROP_NAMES else ""
        print(f"  {n:6d}  {name}{flag}")
    for extra in ("fluids", "entities"):
        if extra in src:
            print(f"source has '{extra}': {len(src[extra])} entries (dropped)")

    # Keep solid blocks, drop the central medallion (replaced by the portal arch), then splice the arch.
    kept = [b for b in blocks if b["name"] not in DROP_NAMES and not is_central_medallion(b)]
    dropped_medallion = sum(1 for b in blocks
                            if b["name"] not in DROP_NAMES and is_central_medallion(b))
    arch = portal_arch()
    kept.extend(arch)
    print(f"dropped central medallion blocks: {dropped_medallion}; spliced portal arch: {len(arch)}")
    # Per-column top to confirm TOP_PLANE_Y is the dominant walkable plane.
    col_top = defaultdict(lambda: -10**9)
    for b in kept:
        col_top[(b["x"], b["z"])] = max(col_top[(b["x"], b["z"])], b["y"])
    plane_cols = Counter(col_top.values())
    xs = [b["x"] for b in kept]
    zs = [b["z"] for b in kept]
    ys = [b["y"] for b in kept]
    print(f"kept solid blocks: {len(kept)}  "
          f"X[{min(xs)}..{max(xs)}] Y[{min(ys)}..{max(ys)}] Z[{min(zs)}..{max(zs)}]")
    print("column-top histogram (top solid Y -> #columns):")
    for y in sorted(plane_cols, reverse=True):
        print(f"  y={y:3d} : {plane_cols[y]} columns")

    if inspect:
        print("\n--inspect only; no file written.")
        return 0

    anchor_y = TOP_PLANE_Y + ANCHOR_RAISE
    out = {
        "version": src.get("version", 8),
        "blockIdVersion": src.get("blockIdVersion", 11),
        "anchorX": 0,
        "anchorY": anchor_y,
        "anchorZ": 0,
        "blocks": [{"x": b["x"], "y": b["y"], "z": b["z"], "name": b["name"]} for b in kept],
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, indent=2)
        f.write("\n")
    print(f"\nwrote {os.path.relpath(OUT, HYMMO_ROOT)} "
          f"({len(kept)} blocks, anchorY={anchor_y})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
