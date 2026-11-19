"""Builds the public site's field wiki (backend/src/main/resources/public/league-fields.json) from the mod's own data:
league_reborn/fields.json (Reborn's field data, as the engine uses it), the mod's French and English texts, and
Cobblemon's move and type names. Run again after a change to the fields:  python tools/league_fields_site.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/phantasmon_events"
COBBLEMON = ROOT.parent / "cobblemon/common/src/main/resources/assets/cobblemon/lang"
OUT = ROOT / "backend/src/main/resources/public/league-fields.json"

fields = json.loads((ASSETS / "league_reborn/fields.json").read_text("utf-8"))
mod = {lang: json.loads((ASSETS / f"lang/{lang}.json").read_text("utf-8")) for lang in ("fr_fr", "en_us")}
cob = {lang: json.loads((COBBLEMON / f"{lang}.json").read_text("utf-8")) for lang in ("fr_fr", "en_us")}

SEEDS = {"elementalseed": ("Graine Élémentaire", "Elemental Seed"), "magicalseed": ("Graine Magique", "Magical Seed"),
         "telluricseed": ("Graine Tellurique", "Telluric Seed"), "syntheticseed": ("Graine Synthétique", "Synthetic Seed")}
STATS = {"atk": ("Attaque", "Attack"), "def": ("Défense", "Defense"), "spa": ("Attaque Spé.", "Sp. Atk"),
         "spd": ("Défense Spé.", "Sp. Def"), "spe": ("Vitesse", "Speed"), "accuracy": ("Précision", "Accuracy"),
         "evasion": ("Esquive", "Evasion")}
CONDS = {"attackerGrounded": ("si l'attaquant touche le sol", "if the attacker is grounded"),
         "targetGrounded": ("si la cible touche le sol", "if the target is grounded")}


def both(fr, en):
    return {"fr": fr, "en": en}


def move(m):
    return both(cob["fr_fr"].get(f"cobblemon.move.{m}", m), cob["en_us"].get(f"cobblemon.move.{m}", m))


def ptype(t):
    if not t:
        return None
    key = f"cobblemon.type.{t.lower()}"
    return both(cob["fr_fr"].get(key, t), cob["en_us"].get(key, t))


def field_name(f):
    if f == "indoor":
        return both("aucun terrain", "no field")
    return both(mod["fr_fr"].get(f"league_reborn.field.{f}", f), mod["en_us"].get(f"league_reborn.field.{f}", f))


def cond(c):
    return both(*CONDS[c]) if c in CONDS else None


out = []
for fid, f in fields.items():
    if fid == "indoor":
        continue
    types = [{"type": ptype(t), "mult": v.get("mult"), "cond": cond(v.get("cond"))} for t, v in f.get("types", {}).items()
             if v.get("mult")]
    boosts, accuracy, changes = [], [], {}
    for m, v in f.get("moves", {}).items():
        if v.get("mult") is not None or v.get("type"):
            boosts.append({"move": move(m), "mult": v.get("mult"), "type": ptype(v.get("type"))})
        if v.get("acc"):
            accuracy.append({"move": move(m), "acc": v["acc"]})
        if v.get("change"):
            changes.setdefault(v["change"], []).append(move(m))
    seed = f.get("seed") or {}
    stats = [{"stat": both(*STATS.get(s, (s, s))), "by": n} for s, n in (seed.get("stats") or {}).items()]
    out.append({
        "id": fid,
        "name": field_name(fid),
        "intro": both(mod["fr_fr"].get(f"league_reborn.{fid}.intro", ""), mod["en_us"].get(f"league_reborn.{fid}.intro", "")),
        "types": types,
        "boosts": sorted(boosts, key=lambda b: -(b["mult"] or 1)),
        "accuracy": accuracy,
        "changes": [{"to": field_name(to), "moves": ms} for to, ms in changes.items()],
        "naturePower": move(f["naturePower"]) if f.get("naturePower") else None,
        "mimicry": ptype(f.get("mimicry")),
        "statusMods": [move(m) for m in f.get("statusMods", [])],
        "seed": {"item": both(*SEEDS[seed["item"]]), "stats": stats} if seed.get("item") in SEEDS else None,
    })

out.sort(key=lambda x: x["name"]["fr"])
OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), "utf-8")
print(f"{len(out)} fields -> {OUT}")
