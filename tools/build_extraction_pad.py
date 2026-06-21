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

# anchorY = TOP_PLANE_Y + ANCHOR_RAISE. RAISING this pins a HIGHER source block to the floor-snapped
# surface, so the whole platform spawns LOWER in the world (1 unit = 1 block lower). 2 = spawn 2 blocks
# lower. To keep the lowered circle VISIBLE (not buried under terrain), the tool also adds Empty "carve"
# cells over the walkable footprint (see carve_hollow) and the exit is pasted with force=true
# (ArenaBuilder.pasteExit), so it digs a clean recess instead of vanishing under the ground. Set 0 for a
# flush platform (no recess); negative raises it.
ANCHOR_RAISE = 2

# Block names dropped on copy (no air, no encounter trigger, no construction signs).
DROP_NAMES = {"Empty", "Block_Spawner_Block", "Furniture_Construction_Sign"}

# The full elemental circle (incl. its central crystal medallion) is KEPT intact; the ONLY addition is a
# cluster of the mod's own glowing Moonbloom plant placed ON the platform (crowning the central columns),
# as the exit marker. No hand-built portal/blocks. The Moonbloom plant glows and is on-theme.
MOONBLOOM = "KweebecNightmare_Moonbloom_Plant"
# Central radius (in X and Z) whose column tops get crowned with a Moonbloom plant.
MOONBLOOM_RADIUS = 2
# The Moonbloom plant needs a FULL solid face below it (Support.Down=Full), so it only goes on top of a
# full block - NOT on half-blocks / stairs / crystal models (it would be skipped by the force=false paste).
FULL_FACE_BLOCKS = {
    "Rock_Basalt_Brick_Smooth",
    "Rock_Basalt_Brick_Decorative",
    "Rock_Basalt_Brick_Ornate",
    "Rock_Crystal_Blue_Block",
}


def moonbloom_cluster(kept):
    """Place a Moonbloom plant ON TOP of each central column of the kept circle whose top block is FULL.

    Robust: it reads the actual top block of every column within MOONBLOOM_RADIUS of the centre and, only
    when that top is a full-face block (so the plant's Support.Down requirement is met), puts one Moonbloom
    one block above it. So each plant sits on a real full surface (the medallion or the walkable ring) and
    actually renders, never floating or silently skipped."""
    col_top_y = {}
    col_top_name = {}
    for b in kept:
        if abs(b["x"]) <= MOONBLOOM_RADIUS and abs(b["z"]) <= MOONBLOOM_RADIUS:
            k = (b["x"], b["z"])
            if b["y"] > col_top_y.get(k, -10 ** 9):
                col_top_y[k] = b["y"]
                col_top_name[k] = b["name"]
    return [{"x": x, "y": y + 1, "z": z, "name": MOONBLOOM}
            for (x, z), y in col_top_y.items()
            if col_top_name[(x, z)] in FULL_FACE_BLOCKS]


def carve_hollow(kept):
    """Empty 'carve' cells so a LOWERED platform (ANCHOR_RAISE > 0) digs a clean recess instead of being
    buried under the terrain. Over every column of the circle, clear the ANCHOR_RAISE+1 cells just above the
    walkable plane (the rows that would otherwise be filled with surrounding ground), but NEVER a cell the
    platform itself occupies (a solid block or a Moonbloom), so the medallion + Moonbloom survive. Only
    meaningful when pasted with force=true (force=false ignores Empty). Empty when ANCHOR_RAISE <= 0."""
    if ANCHOR_RAISE <= 0:
        return []
    occupied = {(b["x"], b["y"], b["z"]) for b in kept}
    cols = {(b["x"], b["z"]) for b in kept if b["name"] != MOONBLOOM}
    lo = TOP_PLANE_Y + 1
    hi = TOP_PLANE_Y + ANCHOR_RAISE + 1  # clear the recess depth + 1 headroom row
    empties = []
    for (x, z) in cols:
        for sy in range(lo, hi + 1):
            if (x, sy, z) not in occupied:
                empties.append({"x": x, "y": sy, "z": z, "name": "Empty"})
    return empties


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

    # Keep ALL solid blocks (the full elemental circle, incl. its central medallion), then crown the
    # centre with the mod's glowing Moonbloom plant as the exit marker.
    kept = [b for b in blocks if b["name"] not in DROP_NAMES]
    moonbloom = moonbloom_cluster(kept)
    kept.extend(moonbloom)
    hollow = carve_hollow(kept)
    kept.extend(hollow)
    print(f"kept full circle; added {len(moonbloom)} Moonbloom plants; {len(hollow)} carve (Empty) cells "
          f"for a {ANCHOR_RAISE}-block recess")
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
