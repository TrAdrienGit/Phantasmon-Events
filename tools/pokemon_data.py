"""Writes the Pokemon catalogue of the Trahison puzzles: backend/src/main/resources/trahison/pokemon_catalog.json.

From Cobblemon's species data (dex number, types, weight, height, evolutions) and Showdown's type chart (efficacies).
Only generations 1 to 3 (dex numbers up to 386) are kept: enough variety, and species most players know. Weight is in
hectograms and height in decimetres, as Cobblemon stores them. The "famous" list is written by hand below.

Run: python tools/pokemon_data.py [cobblemon repo] [pokemon-showdown repo]   (defaults: W:/GitHub/cobblemon, W:/GitHub/pokemon-showdown)
"""
import glob
import json
import os
import re
import sys

MAX_DEX = 386
OUT = os.path.join(os.path.dirname(__file__), "..", "backend", "src", "main", "resources", "trahison", "pokemon_catalog.json")

# Dex numbers of the species almost everyone knows (kept short on purpose).
FAMOUS = [1, 4, 7, 12, 25, 39, 52, 54, 63, 66, 74, 92, 94, 95, 129, 130, 131, 132, 133, 143, 149, 150, 151,
          152, 155, 158, 175, 197, 249, 252, 255, 258, 282, 330, 359, 384, 385, 386, 6, 9, 3, 26, 35, 37, 58, 59, 79, 123,
          134, 135, 136, 147, 196, 248, 257, 260, 373, 376]


def main():
    cobble = sys.argv[1] if len(sys.argv) > 1 else "W:/GitHub/cobblemon"
    showdown = sys.argv[2] if len(sys.argv) > 2 else "W:/GitHub/pokemon-showdown"
    species = {}
    for path in glob.glob(os.path.join(cobble, "common/src/main/resources/data/cobblemon/species/generation[123]/*.json")):
        d = json.load(open(path, encoding="utf-8"))
        n = d.get("nationalPokedexNumber", 0)
        if not 1 <= n <= MAX_DEX:
            continue
        key = os.path.splitext(os.path.basename(path))[0]
        types = [d["primaryType"]] + ([d["secondaryType"]] if d.get("secondaryType") else [])
        species[key] = {
            "dex": n, "id": key, "name": d["name"], "types": types, "weight": d["weight"], "height": d["height"],
            "evolvesTo": sorted({e["result"].split(" ")[0] for e in d.get("evolutions", [])}),
            "evolvesFrom": d.get("preEvolution"),
        }
    for s in species.values():
        s["evolvesTo"] = [e for e in s["evolvesTo"] if e in species]
        if s["evolvesFrom"] not in species:
            s["evolvesFrom"] = None
    # the evolution line: every species of the connected family, in dex order
    for s in species.values():
        root = s
        while root["evolvesFrom"]:
            root = species[root["evolvesFrom"]]
        line, stack = [], [root["id"]]
        while stack:
            x = stack.pop()
            line.append(x)
            stack.extend(species[x]["evolvesTo"])
        s["line"] = sorted(line, key=lambda i: species[i]["dex"])
    by_dex = {s["dex"]: s["id"] for s in species.values()}
    famous = [by_dex[n] for n in dict.fromkeys(FAMOUS) if n in by_dex]

    text = open(os.path.join(showdown, "data/typechart.ts"), encoding="utf-8").read()
    chart = {}
    for m in re.finditer(r"\n\t(\w+): \{\n\t\tdamageTaken: \{(.*?)\n\t\t\},", text, re.S):
        defender = m.group(1)
        if defender == "stellar":
            continue
        for a, code in re.findall(r"\b([A-Z]\w+): (\d)", m.group(2)):
            attacker = a.lower()
            if attacker == "stellar":
                continue
            mult = {"0": 1.0, "1": 2.0, "2": 0.5, "3": 0.0}[code]
            chart.setdefault(attacker, {})[defender] = mult
    out = {"types": sorted(chart), "chart": {a: {d: chart[a][d] for d in sorted(chart[a])} for a in sorted(chart)},
           "famous": famous, "species": [species[i] for i in sorted(species, key=lambda i: species[i]["dex"])]}
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(len(species), "species,", len(famous), "famous,", len(chart), "types ->", OUT)


if __name__ == "__main__":
    main()
