#!/usr/bin/env python3
"""Remove Moonbloom plant cells baked DIRECTLY ADJACENT (orthogonally, excluding
diagonals) to a shrine FURNACE in every KweebecNightmare Shrine_* prefab.

Why: the copied-vanilla shrine host prefabs bake a few KweebecNightmare_Moonbloom_Plant
cells right up against the furnace (KweebecNightmare_Shrine) block. The design wants
Moonbloom GATHERED away from the shrine and CARRIED to the furnace, so a plant touching
the furnace (a free harvest-and-offer at the same spot) is removed. "Directly adjacent
excluding diagonally" = XZ Manhattan distance 1 to any furnace cell (the 4 cardinal
neighbours N/S/E/W); diagonal (dx,dz both +-1) plants are kept.

Format-preserving: these prefabs are an EXPORTED shape (an inline `entities` array
carrying the load-bearing `kn_shrine_marker` detection entity, then an expanded
`blocks` array). A JSON round-trip would reformat the entities block, so this does
TEXT SURGERY on the `blocks` array region ONLY - it parses the JSON purely to decide
which cells to drop, then rebuilds the blocks array from the original per-cell text
(each cell's bytes preserved verbatim, just the adjacent Moonbloom cells dropped and
the survivors re-joined). Everything before `"blocks": [` is left byte-identical.

Usage:
  python strip_shrine_adjacent_moonbloom.py           # DRY RUN (report only)
  python strip_shrine_adjacent_moonbloom.py --apply    # write the edits
"""

from __future__ import annotations

import glob
import json
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PACK_PREFAB_DIR = os.path.join(
    SCRIPT_DIR, "..", "src", "main", "resources", "Server", "Prefabs", "KweebecNightmare"
)

SHRINE = "KweebecNightmare_Shrine"
MOONBLOOM = "KweebecNightmare_Moonbloom_Plant"


def split_top_level_objects(body: str) -> list[str]:
    """Split a JSON array body into its top-level `{...}` object texts, verbatim.

    A brace-depth scan, NOT a regex - some block cells carry a nested `components`
    object (e.g. a bed's RespawnBlock, a crop's FarmingBlock), so a flat brace match
    would mis-segment them. Each returned string is one whole cell's original bytes.
    """
    objs: list[str] = []
    depth = 0
    start = -1
    in_str = False
    escape = False
    for i, ch in enumerate(body):
        if in_str:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == "{":
            if depth == 0:
                start = i
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                objs.append(body[start:i + 1])
    return objs


def adjacent_to_furnace(b: dict, furnace_xz: set[tuple[int, int]]) -> bool:
    """True iff (x,z) is a cardinal (4-neighbour) of any furnace cell's (x,z)."""
    for (sx, sz) in furnace_xz:
        if abs(b["x"] - sx) + abs(b["z"] - sz) == 1:
            return True
    return False


def process(path: str, apply: bool) -> tuple[int, list]:
    with open(path, encoding="utf-8", newline="") as fh:
        text = fh.read()
    data = json.loads(text)
    blocks = data.get("blocks")
    if not isinstance(blocks, list):
        return 0, []

    furnace_xz = {(b["x"], b["z"]) for b in blocks if b.get("name") == SHRINE}
    if not furnace_xz:
        return 0, []

    remove_idx = set()
    removed = []
    for i, b in enumerate(blocks):
        if b.get("name") == MOONBLOOM and adjacent_to_furnace(b, furnace_xz):
            remove_idx.add(i)
            removed.append((b["x"], b["y"], b["z"]))
    if not remove_idx:
        return 0, []

    # Isolate the blocks-array region. Block objects use only `{}` (no nested `[]`), so the
    # FIRST `]` after `"blocks": [` is unambiguously the array close - regardless of where the
    # brace-heavy `entities` array sits relative to `blocks`.
    key = re.search(r'"blocks"\s*:\s*\[', text)
    if key is None:
        raise SystemExit(f"{path}: could not locate the blocks array")
    open_pos = key.end()
    close_pos = text.index("]", open_pos)
    body = text[open_pos:close_pos]
    objs = split_top_level_objects(body)
    if len(objs) != len(blocks):
        raise SystemExit(
            f"{path}: block-text count {len(objs)} != parsed count {len(blocks)} "
            f"(unexpected format; aborting to avoid corruption)"
        )

    kept = [objs[i] for i in range(len(blocks)) if i not in remove_idx]
    # Preserve the prefab's EXACT spacing: the whitespace before the first cell is also the
    # per-cell separator's whitespace, and the whitespace after the last cell precedes `]`.
    first = body.index("{")
    last = body.rindex("}")
    prefix_ws = body[:first]            # e.g. "\n  "
    suffix_ws = body[last + 1:]         # e.g. "\n "
    new_body = prefix_ws + ("," + prefix_ws).join(kept) + suffix_ws
    new_text = text[:open_pos] + new_body + text[close_pos:]

    # STRICT proof: the result must parse, and its blocks must be EXACTLY the original
    # blocks minus the removed indices (byte-for-byte content, not just a count).
    check = json.loads(new_text)
    expected = [b for i, b in enumerate(blocks) if i not in remove_idx]
    if check.get("blocks") != expected:
        raise SystemExit(f"{path}: post-edit blocks differ from expected; aborting")

    if apply:
        with open(path, "w", encoding="utf-8", newline="") as fh:
            fh.write(new_text)
    return len(removed), removed


def main(argv: list[str]) -> None:
    apply = "--apply" in argv
    files = sorted(glob.glob(os.path.join(PACK_PREFAB_DIR, "Shrine_*", "*.prefab.json")))
    if not files:
        raise SystemExit(f"no Shrine_*.prefab.json under {PACK_PREFAB_DIR}")
    total = 0
    for f in files:
        n, removed = process(f, apply)
        total += n
        name = os.path.basename(f)
        if n:
            cells = ", ".join(f"({x},{y},{z})" for x, y, z in removed)
            print(f"{'EDIT' if apply else 'WOULD EDIT'} {name}: removed {n} adjacent Moonbloom -> {cells}")
        else:
            print(f"  ok   {name}: no adjacent Moonbloom")
    print(f"\n{'Removed' if apply else 'Would remove'} {total} Moonbloom cell(s) across {len(files)} prefab(s).")
    if not apply:
        print("Dry run - re-run with --apply to write.")


if __name__ == "__main__":
    main(sys.argv[1:])
