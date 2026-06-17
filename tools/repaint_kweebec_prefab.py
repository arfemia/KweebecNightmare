#!/usr/bin/env python3
"""Corruption-repaint generator for native Kweebec prefabs (Track A, cycle 3).

Reads a native v8 Kweebec structure prefab (the flat JSON shape
{version, blockIdVersion, anchorX/Y/Z, blocks:[{x,y,z,name}], (optional) entities,
(optional) fluids}) and writes a Corrupted_<name>.prefab.json into the pack's
Server/Prefabs/KweebecNightmare/ dir, with every Kweebec-palette block swapped to
a Void-blight equivalent.

Why a build-time DATA edit (no engine hook): PrefabUtil.paste reads the block
"name" straight out of the prefab buffer (PrefabUtil.java:156-194); there is NO
per-cell repaint hook at paste time, so a recolor MUST live in the prefab file.
The committed Corrupted_* outputs mean the runtime never touches hytale-resources.

Determinism: when a source block maps to a SET of blight targets, the pick is a
hash of (x, y, z). The same structure looks identical every run; NO random module.

Validation: before writing, every OUTPUT block name (swapped or passed through)
must be a known block id. The id set is the UNION of:
  - Server/BlockTypeList/*.json   (the worldgen material-curation lists)
  - items-index.json              (the canonical 3641-id item/block mirror)
plus two engine sentinels that legitimately never appear in either list:
  - "Empty"                       (the air sentinel)
  - any name beginning with "*"   (a v8 block-STATE-definition reference, e.g.
                                    "*Wood_Hardwood_Planks_Half_State_Definitions_Block"
                                    or "*Plant_Crop_Carrot_Block_State_Definitions_Stage1";
                                    these resolve at paste and are pass-through only)
BlockTypeList alone is INCOMPLETE (it omits every Furniture_Kweebec_*, Soil_Clay_Brick,
Soil_Pathway, Container_Bucket, Wood_Hardwood_Planks_Half, ... that shipped prefabs
use), so validating against it alone would falsely reject valid pass-through blocks;
items-index.json is the completeness backstop. If any output id is still unknown the
run ABORTS and lists the offenders rather than writing a broken prefab.

Entities: the output "entities" array is forced to [] - the native structures carry
NPC_Spawn_Marker / NPC_Path_Marker entities we do NOT want spawning. The runtime
paste does not load them, but zeroing them is belt-and-suspenders.

Usage:
  python repaint_kweebec_prefab.py <source.prefab.json> <OutName>
    e.g. python repaint_kweebec_prefab.py \
         ../../../hytale-resources/.../Kweebec_Oak_Well_001.prefab.json Well
    writes ../src/main/resources/Server/Prefabs/KweebecNightmare/Corrupted_Well.prefab.json

  python repaint_kweebec_prefab.py --all
    regenerates the whole committed curated set from the hard-coded source map.
"""

from __future__ import annotations

import json
import os
import sys
import glob
from typing import Dict, List, Optional, Set, Tuple

# ---------------------------------------------------------------------------
# Paths (resolved relative to this script so the run is location-independent).
# ---------------------------------------------------------------------------
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
# tools/ -> kweebec-nightmare/ -> additional-mods/ -> hyMMO/
HYMMO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))
RESOURCES = os.path.join(HYMMO_ROOT, "hytale-resources")
BLOCKTYPELIST_DIR = os.path.join(RESOURCES, "assets", "Server", "BlockTypeList")
ITEMS_INDEX = os.path.join(RESOURCES, "items-index.json")
NATIVE_KWEEBEC = os.path.join(
    RESOURCES, "assets", "Server", "Prefabs", "Npc", "Kweebec"
)
PACK_PREFAB_DIR = os.path.join(
    SCRIPT_DIR, "..", "src", "main", "resources", "Server", "Prefabs", "KweebecNightmare"
)

# Engine sentinels that are valid block names but never appear in the id lists.
AIR_SENTINEL = "Empty"
# A name starting with this marks a v8 block-state-definition reference (pass-through).
STATE_DEF_PREFIX = "*"

# ---------------------------------------------------------------------------
# Swap table. Verified families (full suffix sets present in
# BlockTypeList/PlantsAndTrees.json + Soils.json + items-index.json).
# A value that is a tuple/list = a SET; the per-cell pick hashes (x,y,z).
# ---------------------------------------------------------------------------
LEAF_BLIGHT = ("Plant_Leaves_Poisoned", "Plant_Leaves_Petrified", "Plant_Leaves_Dead")
WOOD_BLIGHT_PREFIXES = ("Wood_Poisoned_", "Wood_Petrified_", "Wood_Ash_")

# Wood suffixes we corrupt (everything after the species, e.g. Wood_Oak_<SUFFIX>).
WOOD_SUFFIXES = (
    "Trunk_Full",      # longest first so prefix matching is unambiguous
    "Trunk",
    "Branch_Corner",
    "Branch_Long",
    "Branch_Short",
    "Roots",
)

# Source leaf families that map to LEAF_BLIGHT (any other Plant_Leaves_* species too).
# We match by the "Plant_Leaves_" prefix below, so this tuple is documentation only.
LEAF_SPECIES_DOC = ("Plant_Leaves_Oak", "Plant_Leaves_Autumn", "Plant_Leaves_Redwood",
                    "Plant_Leaves_Azure")

# Flat (single-target) and set (multi-target) swaps that are NOT pattern-based.
SOIL_DIRT_BLIGHT = ("Soil_Dirt_Poisoned", "Soil_Dirt_Burnt")
SOIL_GRASS_BLIGHT = ("Soil_Grass_Burnt", "Soil_Grass_Dry")

# Leaf-species blight targets that should NOT themselves be corrupted (already blight).
ALREADY_BLIGHT_LEAVES = set(LEAF_BLIGHT)


def deterministic_pick(options: Tuple[str, ...], x: int, y: int, z: int) -> str:
    """Pick one option by hashing (x,y,z). Stable across runs (no PRNG)."""
    # A small FNV-ish mix over the three signed coords -> a non-negative index.
    h = 2166136261
    for v in (x, y, z):
        h = (h ^ (v & 0xFFFFFFFF)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return options[h % len(options)]


def swap_name(name: str, x: int, y: int, z: int,
              valid_ids: Set[str], fallback_notes: List[str]) -> str:
    """Return the corrupted block name for a source name, or the name unchanged.

    valid_ids is consulted so that if a chosen blight target is missing a suffix we
    fall back to a present one (and record a note). State-def (*) names and Empty are
    never swapped.
    """
    if name == AIR_SENTINEL or name.startswith(STATE_DEF_PREFIX):
        return name

    # --- Leaves: Plant_Leaves_<species> -> a blight leaf (skip already-blight) ---
    if name.startswith("Plant_Leaves_") and name not in ALREADY_BLIGHT_LEAVES:
        return _pick_present(LEAF_BLIGHT, x, y, z, valid_ids, fallback_notes, name)

    # --- Wood: Wood_<species>_<suffix> -> Wood_<blight>_<same suffix> ---
    if name.startswith("Wood_") and not _is_blight_wood(name):
        suffix = _wood_suffix(name)
        if suffix is not None:
            prefix = deterministic_pick(WOOD_BLIGHT_PREFIXES, x, y, z)
            candidate = prefix + suffix
            if candidate in valid_ids:
                return candidate
            # Fall back across the other blight prefixes that DO carry this suffix.
            present = [p + suffix for p in WOOD_BLIGHT_PREFIXES if (p + suffix) in valid_ids]
            if present:
                chosen = present[deterministic_pick_index(present, x, y, z)]
                fallback_notes.append(
                    f"wood suffix '{suffix}': '{candidate}' absent, fell back to '{chosen}'"
                )
                return chosen
            fallback_notes.append(
                f"wood suffix '{suffix}': no blight prefix present, left '{name}' unchanged"
            )
            return name
        # A Wood_ block we do not recognise the suffix of: leave it.
        return name

    # --- Soil_Dirt (exact) -> a blight dirt ---
    if name == "Soil_Dirt":
        return _pick_present(SOIL_DIRT_BLIGHT, x, y, z, valid_ids, fallback_notes, name)

    # --- Soil_Grass / Soil_Grass_Deep -> a blight grass ---
    if name in ("Soil_Grass", "Soil_Grass_Deep"):
        return _pick_present(SOIL_GRASS_BLIGHT, x, y, z, valid_ids, fallback_notes, name)

    # Everything else (Furniture_Kweebec_*, Rock_*, Soil_Pathway, Soil_Clay_Brick,
    # Soil_Leaves, Plant_Grass_*, decor, containers, ...) is left intact.
    return name


def deterministic_pick_index(options: List[str], x: int, y: int, z: int) -> int:
    h = 2166136261
    for v in (x, y, z):
        h = (h ^ (v & 0xFFFFFFFF)) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    return h % len(options)


def _pick_present(options: Tuple[str, ...], x: int, y: int, z: int,
                  valid_ids: Set[str], fallback_notes: List[str], src: str) -> str:
    """Deterministically pick from options, restricting to those that exist."""
    chosen = deterministic_pick(options, x, y, z)
    if chosen in valid_ids:
        return chosen
    present = [o for o in options if o in valid_ids]
    if present:
        alt = present[deterministic_pick_index(present, x, y, z)]
        fallback_notes.append(f"'{chosen}' absent for src '{src}', fell back to '{alt}'")
        return alt
    fallback_notes.append(f"no blight target present for src '{src}', left unchanged")
    return src


def _is_blight_wood(name: str) -> bool:
    return any(name.startswith(p) for p in WOOD_BLIGHT_PREFIXES)


def _wood_suffix(name: str) -> Optional[str]:
    """Extract the structural suffix from Wood_<species>_<suffix>; None if unknown."""
    for suf in WOOD_SUFFIXES:
        if name.endswith("_" + suf):
            return suf
    return None


def build_valid_id_set() -> Set[str]:
    """Union of every BlockTypeList id + every items-index id."""
    ids: Set[str] = set()

    if not os.path.isdir(BLOCKTYPELIST_DIR):
        raise SystemExit(f"ERROR: BlockTypeList dir not found: {BLOCKTYPELIST_DIR}")
    for f in glob.glob(os.path.join(BLOCKTYPELIST_DIR, "*.json")):
        try:
            d = json.load(open(f, encoding="utf-8"))
        except Exception as e:  # noqa: BLE001 - report and continue
            print(f"WARN: could not parse {f}: {e}", file=sys.stderr)
            continue
        for b in d.get("Blocks", []):
            if isinstance(b, str):
                ids.add(b)
            elif isinstance(b, dict):
                for k in ("Id", "Name", "BlockId", "id", "name"):
                    if k in b:
                        ids.add(b[k])
                        break

    if os.path.isfile(ITEMS_INDEX):
        try:
            d = json.load(open(ITEMS_INDEX, encoding="utf-8"))
            for it in d.get("items", []):
                if isinstance(it, dict) and "id" in it:
                    ids.add(it["id"])
        except Exception as e:  # noqa: BLE001
            print(f"WARN: could not parse items-index.json: {e}", file=sys.stderr)
    else:
        print(f"WARN: items-index.json not found at {ITEMS_INDEX}; "
              f"validation rests on BlockTypeList only (incomplete).", file=sys.stderr)

    if not ids:
        raise SystemExit("ERROR: built an EMPTY valid-id set; aborting.")
    return ids


def is_valid_output_name(name: str, valid_ids: Set[str]) -> bool:
    if name == AIR_SENTINEL:
        return True
    if name.startswith(STATE_DEF_PREFIX):
        return True
    return name in valid_ids


def repaint(src_path: str, out_name: str, valid_ids: Set[str]) -> Dict:
    """Read src prefab, repaint, validate, write Corrupted_<out_name>.prefab.json.

    Returns a small result dict for the caller's summary.
    """
    if not os.path.isfile(src_path):
        raise SystemExit(f"ERROR: source prefab not found: {src_path}")

    with open(src_path, encoding="utf-8") as fh:
        src = json.load(fh)

    blocks = src.get("blocks", [])
    if not isinstance(blocks, list):
        raise SystemExit(f"ERROR: '{src_path}' has no 'blocks' list.")

    fallback_notes: List[str] = []
    swapped = 0
    out_blocks: List[Dict] = []
    for b in blocks:
        x, y, z = b["x"], b["y"], b["z"]
        old = b["name"]
        new = swap_name(old, x, y, z, valid_ids, fallback_notes)
        if new != old:
            swapped += 1
        # Preserve any extra per-block keys verbatim, only the name may change.
        nb = dict(b)
        nb["name"] = new
        out_blocks.append(nb)

    # Build the output preserving everything except entities (forced []) + blocks.
    out: Dict = {
        "version": src["version"],
        "blockIdVersion": src["blockIdVersion"],
        "anchorX": src["anchorX"],
        "anchorY": src["anchorY"],
        "anchorZ": src["anchorZ"],
        "blocks": out_blocks,
    }
    # Preserve fluids verbatim if present (they carry no spawn risk; keep the well water).
    if "fluids" in src:
        out["fluids"] = src["fluids"]
    # Belt-and-suspenders: zero entities so no NPC_Spawn_Marker / NPC_Path_Marker spawns.
    out["entities"] = []

    # --- VALIDATE every output id before writing ---
    unknown: Dict[str, int] = {}
    for b in out_blocks:
        if not is_valid_output_name(b["name"], valid_ids):
            unknown[b["name"]] = unknown.get(b["name"], 0) + 1
    if unknown:
        lines = "\n".join(f"    {n}  (x{c})" for n, c in sorted(unknown.items()))
        raise SystemExit(
            f"ERROR: '{out_name}' would emit UNKNOWN block ids; not writing:\n{lines}"
        )

    if len(out_blocks) != len(blocks):
        raise SystemExit(
            f"ERROR: block count drift for '{out_name}': {len(blocks)} -> {len(out_blocks)}"
        )

    os.makedirs(PACK_PREFAB_DIR, exist_ok=True)
    out_file = os.path.join(PACK_PREFAB_DIR, f"Corrupted_{out_name}.prefab.json")
    _write_compact(out, out_file)

    return {
        "out_name": out_name,
        "out_file": os.path.abspath(out_file),
        "src": os.path.abspath(src_path),
        "block_count": len(out_blocks),
        "swapped": swapped,
        "entities": 0,
        "fluids": len(out.get("fluids", [])),
        "fallbacks": fallback_notes,
    }


def _write_compact(obj: Dict, path: str) -> None:
    """Write the prefab with one block object per line (matches the pack's style)."""
    lines: List[str] = ["{"]
    lines.append(f'  "version": {json.dumps(obj["version"])},')
    lines.append(f'  "blockIdVersion": {json.dumps(obj["blockIdVersion"])},')
    lines.append(f'  "anchorX": {json.dumps(obj["anchorX"])},')
    lines.append(f'  "anchorY": {json.dumps(obj["anchorY"])},')
    lines.append(f'  "anchorZ": {json.dumps(obj["anchorZ"])},')

    # blocks
    lines.append('  "blocks": [')
    blk = obj["blocks"]
    for i, b in enumerate(blk):
        comma = "," if i < len(blk) - 1 else ""
        lines.append(f"    {json.dumps(b, separators=(', ', ': '))}{comma}")
    lines.append("  ],")

    # fluids (optional) then entities (always, last so no dangling comma issue)
    if "fluids" in obj:
        fl = obj["fluids"]
        if fl:
            lines.append('  "fluids": [')
            for i, f in enumerate(fl):
                comma = "," if i < len(fl) - 1 else ""
                lines.append(f"    {json.dumps(f, separators=(', ', ': '))}{comma}")
            lines.append("  ],")
        else:
            lines.append('  "fluids": [],')

    lines.append('  "entities": []')
    lines.append("}")

    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------------------
# The committed curated set: out-name -> native source (relative to NATIVE_KWEEBEC).
# Oak variants chosen (the design's default palette).
# ---------------------------------------------------------------------------
CURATED: List[Tuple[str, str]] = [
    ("Well",        "Oak/Well/Kweebec_Oak_Well_001.prefab.json"),
    ("Lamppost",    "Oak/Lampposts/Kweebec_Oak_Lampposts_001.prefab.json"),
    ("Shop",        "Oak/Shops/Kweebec_Oak_Shops_001.prefab.json"),
    ("GuardTower",  "Oak/Guard_Towers/Kweebec_Oak_Guard_Towers_001.prefab.json"),
    ("House_Small", "Oak/Houses_Small/Kweebec_Oak_Houses_Small_001.prefab.json"),
    ("Bridge",      "Oak/Bridge/Kweebec_Oak_Bridge_001.prefab.json"),
]


def run_all() -> None:
    valid_ids = build_valid_id_set()
    print(f"valid-id set: {len(valid_ids)} ids "
          f"(BlockTypeList union items-index.json)")
    results = []
    skipped = []
    for out_name, rel in CURATED:
        src = os.path.join(NATIVE_KWEEBEC, rel.replace("/", os.sep))
        if not os.path.isfile(src):
            skipped.append((out_name, src))
            print(f"SKIP {out_name}: source not found ({src})", file=sys.stderr)
            continue
        r = repaint(src, out_name, valid_ids)
        results.append(r)
        fb = f"  ({len(r['fallbacks'])} fallback notes)" if r["fallbacks"] else ""
        print(f"OK   Corrupted_{out_name}.prefab.json  "
              f"blocks={r['block_count']} swapped={r['swapped']} "
              f"fluids={r['fluids']} entities={r['entities']}{fb}")
        for note in r["fallbacks"]:
            print(f"       note: {note}")
    print(f"\nGenerated {len(results)} prefab(s); skipped {len(skipped)}.")
    if skipped:
        for n, s in skipped:
            print(f"  skipped {n}: {s}")


def main(argv: List[str]) -> None:
    if len(argv) == 1 and argv[0] == "--all":
        run_all()
        return
    if len(argv) == 2:
        src, out_name = argv
        valid_ids = build_valid_id_set()
        r = repaint(src, out_name, valid_ids)
        print(json.dumps(r, indent=2))
        return
    print(__doc__)
    raise SystemExit("usage: repaint_kweebec_prefab.py (--all | <source.prefab.json> <OutName>)")


if __name__ == "__main__":
    main(sys.argv[1:])
