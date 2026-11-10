"""Writes the Expert AI's knowledge of competitive sets: src/main/resources/assets/phantasmon_events/ai/smogon_sets.json.

From Smogon's usage stats ("chaos" files, https://www.smogon.com/stats/<month>/chaos/): for each Pokémon of Gen 9 OU
and National Dex, what is most often run — moves (share of sets), item, ability, EV spread with nature, Tera type.
The AI fills what an opponent hasn't revealed yet with these (it never looks at the real team). Keys and move ids are
Showdown ids (lowercase letters and digits). The two tiers are merged, weighted by how much each Pokémon is used.

Run: python tools/smogon_sets.py <stats dir with gen9ou-1695.json and gen9nationaldex-1630.json>
"""
import json
import os
import re
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "phantasmon_events", "ai", "smogon_sets.json")


def sid(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def main():
    stats_dir = sys.argv[1]
    merged = {}
    for file in ("gen9ou-1695.json", "gen9nationaldex-1630.json"):
        data = json.load(open(os.path.join(stats_dir, file), encoding="utf-8"))["data"]
        for name, mon in data.items():
            weight = sum(mon.get("Abilities", {}).values())
            if weight <= 0:
                continue
            entry = merged.setdefault(sid(name), {"w": 0, "moves": {}, "items": {}, "abilities": {}, "spreads": {}, "tera": {}})
            entry["w"] += weight
            for key, target in (("Moves", "moves"), ("Items", "items"), ("Abilities", "abilities"), ("Spreads", "spreads"),
                                ("Tera Types", "tera")):
                for value, count in mon.get(key, {}).items():
                    if value and value != "nothing":
                        entry[target][value] = entry[target].get(value, 0) + count
    out = {}
    for species, entry in merged.items():
        w = entry["w"]
        moves = sorted(entry["moves"].items(), key=lambda kv: -kv[1])
        moves = [[m, round(c / w, 3)] for m, c in moves if c / w >= 0.05][:10]
        top = lambda key: max(entry[key].items(), key=lambda kv: kv[1])[0] if entry[key] else None
        spread = top("spreads")
        record = {"moves": moves}
        if top("items"):
            record["item"] = top("items")
        if top("abilities"):
            record["ability"] = top("abilities")
        if spread:
            nature, evs = spread.split(":")
            record["nature"] = nature.lower()
            record["evs"] = [int(v) for v in evs.split("/")]
        if top("tera"):
            record["tera"] = top("tera").lower()
        out[species] = record
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, separators=(",", ":"), sort_keys=True)
    print(len(out), "species,", os.path.getsize(OUT) // 1024, "KB")


if __name__ == "__main__":
    main()
