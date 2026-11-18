# Generates the nine room templates from compact data. Hints of steps are read from Documentation/salles/*.md.
import json, re, os

ROOT = r"W:\GitHub\Phantasmon-Events"
OUT = ROOT + r"\src\main\resources\assets\phantasmon_events\trahison\templates"
os.makedirs(OUT, exist_ok=True)

# wall helpers: canonical frame, x east, z south, walls 1 block thick at 0/47, slots on the inner layer 1/46
FACE = {"W": "east", "E": "west", "N": "south", "S": "north"}


def wall(side, along, y, w=1, h=1):
    if side == "W":
        return (1, y, along), (1, h, w), FACE[side]
    if side == "E":
        return (46, y, along), (1, h, w), FACE[side]
    if side == "N":
        return (along, y, 1), (w, h, 1), FACE[side]
    return (along, y, 46), (w, h, 1), FACE[side]


class Room:
    def __init__(self, rid, name, difficulty, sheet):
        self.id, self.name, self.difficulty, self.sheet = rid, name, difficulty, sheet
        self.slots, self.steps = [], []
        self.hints = read_hints(sheet)

    def slot(self, sid, kind, pos, size, orient, hint):
        self.slots.append({"id": sid, "kind": kind, "position": dict(zip("xyz", pos)), "orientation": orient,
                           "size": dict(zip("xyz", size)), "hint": hint, "placed": False})

    def w(self, sid, kind, side, along, y, hint, w=1, h=1):
        pos, size, orient = wall(side, along, y, w, h)
        self.slot(sid, kind, pos, size, orient, hint)

    def p(self, sid, kind, pos, orient, hint, size=(1, 1, 1)):
        self.slot(sid, kind, pos, size, orient, hint)

    def exit(self, hint="Mur nord, au centre : l'ouverture de la sortie. À laisser vide, le jeu la ferme."):
        self.slot("sortie", "exit", (22, 1, 0), (3, 3, 1), "south", hint)

    def step(self, sid, mech, params, slots, needs, gives=(), exit=False):
        s = dict(slots)
        if exit:
            s["exit"] = "sortie"
        g = [{"clue": c, "slot": sl} if len(t) == 2 else {"clue": c, "slot": sl, "when": t[2]}
             for t in gives for c, sl in [(t[0], t[1])]]
        self.steps.append({"id": sid, "mechanism": mech, "params": params, "slots": s, "needs": list(needs),
                           "gives": g, "hints": self.hints[sid]})

    def dump(self):
        d = {"id": self.id, "name": self.name, "difficulty": self.difficulty, "slots": self.slots, "steps": self.steps}
        with open(f"{OUT}\\{self.id}.json", "w", encoding="utf-8", newline="\n") as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
            f.write("\n")


def read_hints(sheet):
    text = open(f"{ROOT}\\Documentation\\salles\\{sheet}.md", encoding="utf-8").read()
    part = text.split("## Les trois aides de chaque étape")[1].split("\n## ")[0]
    out = {}
    for line in part.splitlines():
        m = re.match(r"\|\s*`(\w+)`\s*\|(.*)\|\s*$", line)
        if m:
            cells = [c.strip() for c in m.group(2).split("|")]
            out[m.group(1)] = cells
    return out


def levers(r, ids, corners, label):
    spots = {"NO": ((1, 1, 3), "east", "nord-ouest"), "NE": ((46, 1, 3), "west", "nord-est"),
             "SO": ((1, 1, 44), "east", "sud-ouest"), "SE": ((46, 1, 44), "west", "sud-est")}
    for i, c in zip(ids, corners):
        pos, orient, where = spots[c]
        r.p(i, "lever", pos, orient, f"Coin {where} : {label}, un des trois coins à tenir ensemble.")


# ------------------------------------------------------------------ archives
r = Room("archives", "Les Archives", 2, "archives")
r.exit()
r.p("fiche_1", "pickup", (15, 2, 12), "east", "Rayonnage ouest, au nord : première cachette de fiche.")
r.p("fiche_2", "pickup", (15, 3, 26), "east", "Rayonnage ouest, au sud : deuxième cachette de fiche.")
r.p("fiche_3", "pickup", (32, 2, 16), "west", "Rayonnage est, au nord : troisième cachette de fiche.")
r.p("fiche_4", "pickup", (32, 4, 30), "west", "Rayonnage est, au sud, en hauteur : quatrième cachette.")
r.p("bureau_registre", "document", (3, 2, 38), "east", "Bureau de l'archiviste, à l'ouest près de l'entrée : le registre.")
r.w("pad_casier", "keypad", "S", 6, 1, "Mur sud, côté ouest : le digicode du casier.")
r.w("mur_casier", "wall_clue", "W", 18, 2, "Mur ouest, derrière un rayonnage : la plaque du code du casier.", 2, 2)
r.w("pad_index", "terminal", "E", 24, 1, "Mur est, au milieu : le panneau du fichier à tiroirs.")
r.w("mur_index", "wall_clue", "E", 17, 2, "Mur est, près du fichier : le schéma en couleurs.", 2, 2)
r.p("bureau_index", "document", (3, 2, 42), "east", "Bureau de l'archiviste, côté sud : le schéma en texte.")
r.w("pad_cadran", "terminal", "E", 6, 1, "Mur est, au fond : le coffre à molettes.")
r.p("mur_cadran", "wall_clue", (23, 2, 24), "south", "Centre, sur un pilier : le dos d'une affiche qui porte la date.", (2, 2, 1))
r.w("pad_rayons", "terminal", "N", 12, 1, "Mur nord, devant le mobile de rayonnage : le pupitre des chariots.")
levers(r, ["levier_1", "levier_2", "levier_3"], ["NO", "NE", "SO"], "manette de rayonnage")
r.p("cachette_1", "secret", (45, 6, 26), "west", "Haut du rayonnage le plus à l'est : un livre vide.")
r.p("cachette_2", "secret", (3, 1, 34), "east", "Tiroir du bureau de l'archiviste.")
r.p("cachette_3", "secret", (20, 1, 4), "south", "Sous le mobile de rayonnage, au nord.")
r.step("fiches", "pickup_set", {"spots": 4, "filled": 3, "document": "Registre de l'archiviste\nTrois fiches manquent aux rayons.\nChaque fiche porte un numéro de casier."},
       {}, [], [("spot_1", "fiche_1"), ("spot_2", "fiche_2"), ("spot_3", "fiche_3"), ("spot_4", "fiche_4"), ("document", "bureau_registre")])
r.step("casier", "keypad", {"length": 4}, {"keypad": "pad_casier"}, [], [("code", "mur_casier")])
r.step("index", "wiring", {"wires": 4, "colors": 3}, {"panel": "pad_index"}, ["fiches"], [("scheme", "mur_index"), ("scheme_text", "bureau_index")])
r.step("cadran", "three_dials", {}, {"dials": "pad_cadran"}, ["casier"], [("code", "mur_cadran")])
r.step("rayons", "sliding_blocks", {}, {"terminal": "pad_rayons"}, ["index", "cadran"])
r.step("porte", "three_levers", {"ordered": False, "gap": 5, "hold": 12}, {"lever_1": "levier_1", "lever_2": "levier_2", "lever_3": "levier_3"}, ["rayons"], exit=True)
r.dump()

# ------------------------------------------------------------------ serre
r = Room("serre", "La Serre", 2, "serre")
r.exit()
r.p("graine_1", "pickup", (12, 1, 14), "south", "Premier bac, allée ouest : un sachet de graines.")
r.p("graine_2", "pickup", (20, 1, 22), "south", "Pot de l'allée du centre, au nord : un sachet.")
r.p("graine_3", "pickup", (28, 2, 16), "south", "Bac surélevé de l'allée centrale : un sachet.")
r.p("graine_4", "pickup", (24, 1, 32), "south", "Bac de l'allée du sud : un sachet.")
r.w("pad_dex", "keypad", "W", 24, 1, "Mur ouest, au milieu : le digicode des statues.")
r.w("statue_1", "wall_clue", "W", 12, 3, "Mur ouest, au-dessus des bacs : la première statue.", 2, 2)
r.w("statue_2", "wall_clue", "W", 18, 3, "Mur ouest, au-dessus des bacs : la deuxième statue.", 2, 2)
r.p("bureau_dex", "document", (3, 2, 34), "east", "Bureau de l'ouest : la page du Pokédex.")
r.w("pad_hauteur", "terminal", "E", 32, 1, "Mur est, au milieu : le pupitre pour ranger les socles.")
r.p("socle_1", "pedestal", (43, 1, 18), "west", "Mur est, premier socle d'exposition de la rangée.")
r.p("socle_2", "pedestal", (43, 1, 22), "west", "Mur est, deuxième socle, aligné avec le premier.")
r.p("socle_3", "pedestal", (43, 1, 26), "west", "Mur est, troisième socle, aligné avec les autres.")
r.p("bureau_mesures", "document", (45, 2, 38), "west", "Bureau de l'est : la fiche des mesures.")
r.w("pad_local", "keypad", "S", 40, 1, "Coin sud-est, mur sud : le digicode du local technique.")
r.w("mur_local", "wall_clue", "S", 34, 2, "Mur sud, derrière une plante : la plaque du local.", 2, 2)
r.w("pad_thermo", "terminal", "N", 40, 1, "Mur nord-est : le thermostat à trois molettes.")
r.w("mur_thermo", "wall_clue", "N", 30, 3, "Mur nord, au-dessus des tuyaux : le thermomètre cassé.", 2, 2)
r.w("poste_a", "terminal", "W", 4, 1, "Coin nord-ouest : le premier poste d'arrosage.")
r.w("poste_b", "terminal", "E", 42, 1, "Coin sud-est : le second poste d'arrosage, loin du premier.")
r.p("cachette_1", "secret", (8, 1, 28), "north", "Sous un bac renversé, au pied de la plus grosse plante, côté ouest.")
r.p("cachette_2", "secret", (30, 1, 6), "south", "Dans un pot du fond, près du nord.")
r.step("graines", "pickup_set", {"spots": 4, "filled": 3}, {}, [], [("spot_1", "graine_1"), ("spot_2", "graine_2"), ("spot_3", "graine_3"), ("spot_4", "graine_4")])
r.step("statues", "dex_code", {"count": 2}, {"terminal": "pad_dex"}, [], [("portrait_1", "statue_1"), ("portrait_2", "statue_2"), ("dex_page", "bureau_dex")])
r.step("hauteur", "weigh_in", {"count": 3, "by": "height"}, {"row": "pad_hauteur"}, [], [("pedestal_1", "socle_1"), ("pedestal_2", "socle_2"), ("pedestal_3", "socle_3"), ("scale_sheet", "bureau_mesures")])
r.step("digicode", "keypad", {"length": 3}, {"keypad": "pad_local"}, ["graines"], [("code", "mur_local")])
r.step("thermo", "three_dials", {}, {"dials": "pad_thermo"}, ["statues", "hauteur"], [("code", "mur_thermo")])
r.step("arrosage", "twin_screens", {}, {"left": "poste_a", "right": "poste_b"}, ["digicode", "thermo"], [("left_code", "poste_a"), ("right_code", "poste_b")], exit=True)
r.dump()

# ------------------------------------------------------------------ dortoir
r = Room("dortoir", "Le Dortoir", 2, "dortoir")
r.exit()
r.p("jouet_1", "pickup", (8, 1, 12), "east", "Sous un lit de la rangée ouest, au nord : un jouet perdu.")
r.p("jouet_2", "pickup", (8, 1, 28), "east", "Sous un lit de la rangée ouest, au sud : un jouet perdu.")
r.p("jouet_3", "pickup", (38, 1, 16), "west", "Sous un lit de la rangée est : un jouet perdu.")
r.p("jouet_4", "pickup", (30, 1, 38), "west", "Dans une malle, côté sud : un jouet perdu.")
r.p("chevet_journal", "document", (3, 2, 6), "east", "Chevet ouest, au fond : la page de journal.")
r.w("pad_dessins", "keypad", "S", 18, 1, "Mur sud, près des dessins : le digicode du message.")
r.w("mur_dessins", "wall_clue", "S", 12, 3, "Mur sud, au-dessus du lit : le message dessiné (3 de large).", 3, 2)
r.p("chevet_alphabet", "document", (45, 2, 28), "west", "Chevet est : la page de l'alphabet.")
r.w("pad_veilleuse", "terminal", "N", 40, 2, "Mur nord-est, à hauteur des yeux : le panneau de tuyaux de la veilleuse.")
r.p("pad_malle", "keypad", (23, 1, 26), "south", "Au pied d'un lit, au centre : le digicode de la malle.")
r.w("mur_malle", "wall_clue", "N", 12, 3, "Mur du fond, derrière un cadre : la date.", 2, 2)
levers(r, ["lampe_1", "lampe_2", "lampe_3"], ["NO", "NE", "SO"], "interrupteur de lit")
r.p("cachette_1", "secret", (41, 1, 38), "west", "Une latte du plancher sous le dernier lit de l'est.")
r.p("cachette_2", "secret", (12, 2, 4), "south", "Dans l'oreiller du lit du fond, au nord.")
r.step("peluches", "pickup_set", {"spots": 4, "filled": 3, "document": "Journal de la dormeuse\nLes jouets ont disparu cette nuit.\nIls dorment sous les lits."},
       {}, [], [("spot_1", "jouet_1"), ("spot_2", "jouet_2"), ("spot_3", "jouet_3"), ("spot_4", "jouet_4"), ("document", "chevet_journal")])
r.step("dessins", "unown_text", {"length": 3}, {"terminal": "pad_dessins"}, ["peluches"], [("wall_text", "mur_dessins"), ("alphabet", "chevet_alphabet")])
r.step("veilleuse", "rotating_tiles", {}, {"terminal": "pad_veilleuse"}, [])
r.step("malle", "keypad", {"length": 4}, {"keypad": "pad_malle"}, ["dessins", "veilleuse"], [("code", "mur_malle")])
r.step("lampes", "three_levers", {"ordered": False, "gap": 6, "hold": 14}, {"lever_1": "lampe_1", "lever_2": "lampe_2", "lever_3": "lampe_3"}, ["malle"], exit=True)
r.dump()

# ------------------------------------------------------------------ salle des machines
r = Room("salle_des_machines", "La Salle des machines", 3, "salle_des_machines")
r.exit()
r.p("fusible_1", "pickup", (10, 6, 12), "south", "Passerelle ouest, en hauteur : un fusible.")
r.p("fusible_2", "pickup", (36, 8, 10), "south", "Passerelle nord-est, très haut : un fusible.")
r.p("fusible_3", "pickup", (20, 1, 26), "south", "Au sol, derrière un tuyau du centre : un fusible.")
r.p("fusible_4", "pickup", (40, 3, 30), "west", "Contre les tuyaux de l'est : un fusible.")
r.p("fusible_5", "pickup", (12, 2, 40), "north", "Au sol, côté sud-ouest : un fusible.")
r.w("pad_tableau", "terminal", "W", 24, 1, "Mur ouest : le tableau électrique à câbler.")
r.w("mur_schema", "wall_clue", "W", 24, 3, "Mur ouest, au-dessus du tableau : le schéma de câblage.", 2, 2)
r.p("bureau_notice", "document", (3, 2, 40), "east", "Bureau du sud-ouest : la notice du câblage.")
r.w("pad_engrenages", "terminal", "N", 30, 1, "Mur nord : le pupitre des engrenages.")
r.w("mur_engrenages", "wall_clue", "E", 10, 3, "Cadre sur la grille de l'est : la séquence de symboles.", 2, 2)
for i, z in enumerate((14, 17, 20, 23), 1):
    r.p(f"manette_{i}", "lever", (46, 1, z), "west", f"Mur est, manette {i} sur 4 : alignée avec les autres.")
    r.p(f"marque_{i}", "wall_clue", (46, 2, z), "west", f"Mur est, juste au-dessus de la manette {i} : la marque de type.")
r.p("bureau_types", "document", (3, 2, 43), "east", "Bureau du sud-ouest : la fiche des types.")
r.w("poste_a", "terminal", "E", 4, 1, "Nord-est : le premier pupitre de pression.")
r.w("poste_b", "terminal", "W", 42, 1, "Sud-ouest : le second pupitre de pression, loin du premier.")
r.p("cachette_1", "secret", (24, 1, 22), "south", "Compartiment de maintenance sous la chaudière, au centre.")
r.p("cachette_2", "secret", (3, 4, 14), "east", "Derrière un tuyau de la passerelle ouest.")
r.step("fusibles", "pickup_set", {"spots": 5, "filled": 4}, {}, [], [(f"spot_{i}", f"fusible_{i}") for i in range(1, 6)])
r.step("tableau", "wiring", {"wires": 5, "colors": 4}, {"panel": "pad_tableau"}, ["fusibles"], [("scheme", "mur_schema"), ("scheme_text", "bureau_notice")])
r.step("engrenages", "ring_dial", {"rings": 3, "symbols": 6, "links": 1}, {"terminal": "pad_engrenages"}, ["tableau"], [("symbols", "mur_engrenages")])
r.step("vannes", "type_chain", {"count": 4}, {f"lever_{i}": f"manette_{i}" for i in range(1, 5)}, ["fusibles"],
       [(f"mark_{i}", f"marque_{i}") for i in range(1, 5)] + [("type_sheet", "bureau_types")])
r.step("pression", "twin_screens", {}, {"left": "poste_a", "right": "poste_b"}, ["engrenages", "vannes"], [("left_code", "poste_a"), ("right_code", "poste_b")], exit=True)
r.dump()

# ------------------------------------------------------------------ infirmerie
r = Room("infirmerie", "L'Infirmerie", 3, "infirmerie")
r.exit()
r.w("pad_armoire", "keypad", "N", 38, 1, "Mur nord-est : le digicode de l'armoire.")
r.w("mur_dossier", "wall_clue", "N", 4, 2, "Nord-ouest, derrière le rideau du dernier lit : le dossier.", 2, 2)
r.w("pupitre_cris", "terminal", "S", 12, 1, "Mur sud : le pupitre du haut-parleur des cris.")
r.w("pad_perfusion", "terminal", "W", 26, 2, "Mur ouest, à hauteur des yeux : le panneau de perfusion.")
r.w("pad_pc", "terminal", "E", 42, 1, "Angle sud-est : le PC de soins.")
r.w("pad_pharmacie", "terminal", "N", 42, 1, "Mur nord-est, près de l'armoire : l'armoire à produits.")
levers(r, ["bip_1", "bip_2", "bip_3"], ["NO", "NE", "SO"], "interrupteur de lit")
r.p("cachette_1", "secret", (30, 1, 20), "south", "Tiroir sous le moniteur cardiaque.")
r.p("cachette_2", "secret", (10, 1, 22), "east", "Derrière le rideau d'un lit de l'ouest.")
r.step("dossier", "keypad", {"length": 4}, {"keypad": "pad_armoire"}, [], [("code", "mur_dossier")])
r.step("cris", "cry_match", {"count": 3}, {"console": "pupitre_cris"}, [])
r.step("perfusion", "rotating_tiles", {}, {"terminal": "pad_perfusion"}, ["dossier"])
r.step("pc_soins", "pc_box", {"count": 6, "fixed": 3}, {"terminal": "pad_pc"}, ["cris"])
r.step("pharmacie", "code_breaker", {"length": 4, "alphabet": 6, "kind": "symbols"}, {"terminal": "pad_pharmacie"}, ["perfusion", "pc_soins"])
r.step("bips", "three_levers", {"ordered": True, "gap": 5, "hold": 12}, {"lever_1": "bip_1", "lever_2": "bip_2", "lever_3": "bip_3"}, ["pharmacie"], exit=True)
r.dump()

# ------------------------------------------------------------------ bal
r = Room("bal", "La Salle de bal", 3, "bal")
r.exit()
r.p("zone_sombre", "power_spot", (2, 1, 14), "east", "Aile ouest : la zone noire, 5 x 3 x 5. Ne rien construire dedans.", (5, 3, 5))
r.w("pad_dex", "keypad", "W", 34, 1, "Mur ouest, hors de la zone sombre : le digicode des portraits.")
r.w("cadre_1", "wall_clue", "W", 15, 2, "Mur ouest, dans la zone sombre : le premier portrait.", 2, 2)
r.w("cadre_2", "wall_clue", "W", 18, 2, "Mur ouest, dans la zone sombre : le second portrait.", 2, 2)
r.p("table_dex", "document", (30, 2, 43), "east", "Table près de l'entrée : la page du Pokédex.")
r.w("pad_vestiaire", "keypad", "S", 10, 1, "Mur sud : le digicode du vestiaire.")
r.p("carnet_bal", "document", (24, 2, 24), "south", "Sur le piano, au centre : le carnet de bal.")
r.p("boite_musique", "terminal", (30, 1, 6), "south", "Piédestal au nord : la boîte à musique.")
r.w("pad_pendule", "terminal", "E", 24, 1, "Mur est : le cadran de la pendule.")
r.w("mur_miroir", "wall_clue", "E", 30, 2, "Mur est, le grand miroir : les symboles gravés.", 2, 2)
levers(r, ["chaine_1", "chaine_2", "chaine_3"], ["NE", "SE", "SO"], "chaîne de lustre")
r.p("cachette_1", "secret", (46, 3, 38), "west", "Derrière un miroir voilé du mur est.")
r.p("cachette_2", "secret", (24, 1, 28), "south", "Dans le piano, côté sud.")
r.p("cachette_3", "secret", (28, 1, 8), "south", "Sous une latte du parquet, près de la boîte à musique.")
r.step("lumiere", "power_spot", {"power": "FLASH", "radius": 4}, {"spot": "zone_sombre"}, [])
r.step("portraits", "dex_code", {"count": 2}, {"terminal": "pad_dex"}, ["lumiere"], [("portrait_1", "cadre_1"), ("portrait_2", "cadre_2"), ("dex_page", "table_dex")])
r.step("vestiaire", "keypad", {"length": 4}, {"keypad": "pad_vestiaire"}, [], [("code", "carnet_bal")])
r.step("musique", "safe_dial", {}, {"terminal": "boite_musique"}, ["portraits"])
r.step("miroirs", "ring_dial", {"rings": 3, "symbols": 6, "links": 1}, {"terminal": "pad_pendule"}, ["vestiaire"], [("symbols", "mur_miroir")])
r.step("lustres", "three_levers", {"ordered": True, "gap": 5, "hold": 12}, {"lever_1": "chaine_1", "lever_2": "chaine_2", "lever_3": "chaine_3"}, ["musique", "miroirs"], exit=True)
r.dump()

# ------------------------------------------------------------------ observatoire
r = Room("observatoire", "L'Observatoire", 4, "observatoire")
r.exit()
r.w("pad_carte", "keypad", "S", 6, 1, "Mur sud-ouest : le digicode du tiroir des cartes.")
r.p("carte_ciel", "document", (4, 2, 38), "east", "Table des cartes, au sud-ouest : la carte qui porte le code (les autres sont du décor).")
r.w("radio", "terminal", "W", 20, 1, "Mur ouest : la radio qui capte les cris.")
r.p("pad_astrolabe", "terminal", (24, 1, 24), "south", "Au centre : le pupitre de l'astrolabe.")
r.w("mur_constellation", "wall_clue", "E", 8, 8, "Mur est, en haut : la constellation gravée.", 2, 2)
r.w("pad_plafond", "terminal", "N", 14, 1, "Mur nord : le pupitre des tuiles du plafond.")
r.w("pad_serrure", "terminal", "E", 28, 1, "Mur est : la lunette de visée de la serrure.")
r.p("lunette_a", "terminal", (3, 7, 4), "south", "Balcon nord-ouest, à 6 de haut : la première lunette.")
r.p("lunette_b", "terminal", (44, 7, 42), "north", "Balcon sud-est, à 6 de haut : la seconde lunette, loin de la première.")
r.p("cachette_1", "secret", (24, 1, 18), "south", "Dans le pied du grand télescope, au centre.")
r.p("cachette_2", "secret", (40, 1, 16), "west", "Sous le fauteuil de l'observateur, côté est.")
r.step("carte", "keypad", {"length": 5}, {"keypad": "pad_carte"}, [], [("code", "carte_ciel")])
r.step("radio", "cry_match", {"count": 4}, {"console": "radio"}, [])
r.step("astrolabe", "ring_dial", {"rings": 4, "symbols": 7, "links": 2}, {"terminal": "pad_astrolabe"}, ["carte"], [("symbols", "mur_constellation")])
r.step("constellation", "rotating_tiles", {}, {"terminal": "pad_plafond"}, ["radio"])
r.step("serrure", "code_breaker", {"length": 4, "alphabet": 7, "kind": "symbols", "maxTries": 12}, {"terminal": "pad_serrure"}, ["astrolabe", "constellation"])
r.step("lunettes", "twin_screens", {}, {"left": "lunette_a", "right": "lunette_b"}, ["serrure"], [("left_code", "lunette_a"), ("right_code", "lunette_b")], exit=True)
r.dump()

# ------------------------------------------------------------------ chapelle
r = Room("chapelle", "La Chapelle", 4, "chapelle")
r.exit()
r.w("pad_inscriptions", "keypad", "W", 28, 1, "Mur ouest : le digicode des inscriptions.")
r.w("mur_inscriptions", "wall_clue", "W", 14, 5, "Mur ouest, en haut : les inscriptions (3 de large).", 3, 2)
r.p("sacristie", "document", (19, 2, 3), "south", "Derrière l'autel, au nord : la sacristie cachée, avec l'alphabet.")
r.w("pad_saints", "terminal", "E", 34, 1, "Mur est : le pupitre pour ranger les saints.")
for i, z in enumerate((8, 12, 16, 20, 24), 1):
    r.p(f"niche_{i}", "pedestal", (45, 2, z), "west", f"Mur est, niche {i} sur 5 : alignée avec les autres.")
r.p("pupitre_mesures", "document", (44, 1, 29), "west", "Près des niches : le pupitre des mesures.")
r.p("pad_bancs", "terminal", (16, 1, 9), "south", "Devant l'autel, à gauche : le pupitre des bancs.")
r.p("reliquaire", "terminal", (30, 1, 9), "south", "Devant l'autel, à droite : le reliquaire à molette.")
r.w("pad_orgue", "terminal", "S", 30, 1, "Mur sud : les tuyaux de l'orgue.")
levers(r, ["cloche_1", "cloche_2", "cloche_3"], ["NO", "NE", "SO"], "cloche")
r.p("cachette_1", "secret", (32, 1, 44), "north", "Dans le pupitre de l'orgue, sous le clavier.")
r.p("cachette_2", "secret", (23, 3, 6), "south", "Dans le tabernacle de l'autel.")
r.step("inscriptions", "unown_text", {"length": 4}, {"terminal": "pad_inscriptions"}, [], [("wall_text", "mur_inscriptions"), ("alphabet", "sacristie")])
r.step("saints", "weigh_in", {"count": 5, "by": "height"}, {"row": "pad_saints"}, [], [(f"pedestal_{i}", f"niche_{i}") for i in range(1, 6)] + [("scale_sheet", "pupitre_mesures")])
r.step("bancs", "sliding_blocks", {}, {"terminal": "pad_bancs"}, ["inscriptions"])
r.step("reliquaire", "safe_dial", {}, {"terminal": "reliquaire"}, ["saints"])
r.step("orgue", "code_breaker", {"length": 5, "alphabet": 8, "kind": "symbols", "maxTries": 14}, {"terminal": "pad_orgue"}, ["bancs", "reliquaire"])
r.step("cloches", "three_levers", {"ordered": True, "gap": 4, "hold": 10}, {"lever_1": "cloche_1", "lever_2": "cloche_2", "lever_3": "cloche_3"}, ["orgue"], exit=True)
r.dump()

# ------------------------------------------------------------------ crypte
r = Room("crypte", "La Crypte", 4, "crypte")
r.exit()
r.p("os_1", "pickup", (8, 1, 12), "east", "Caveau de l'ouest, au nord : une clef d'os.")
r.p("os_2", "pickup", (14, 2, 22), "east", "Niche du caveau du centre-ouest : une clef d'os.")
r.p("os_3", "pickup", (38, 1, 12), "west", "Caveau de l'est, au nord : une clef d'os.")
r.p("os_4", "pickup", (34, 2, 24), "west", "Niche du caveau du centre-est : une clef d'os.")
r.p("os_5", "pickup", (24, 1, 30), "north", "Caveau du sud, au centre : une clef d'os.")
r.w("pad_grille", "terminal", "S", 12, 1, "Mur sud : la grille d'entrée du caveau.")
r.w("mur_grille", "wall_clue", "S", 14, 4, "Mur sud, en haut : le schéma de la grille.", 2, 2)
r.p("pierre_grille", "document", (12, 1, 43), "north", "Dalle devant la grille : le schéma en texte.")
r.p("pad_caveau", "terminal", (24, 1, 24), "south", "Au centre : les molettes du caveau central.")
r.p("mur_caveau", "wall_clue", (22, 2, 20), "south", "Caveau central : la gravure du code.", (2, 2, 1))
r.p("pad_boite", "terminal", (6, 1, 6), "south", "Sarcophage nord-ouest : le PC de la crypte.")
r.p("arene", "terminal", (40, 1, 6), "south", "Sarcophage nord-est : l'arène du duel.")
r.w("mur_sceau", "wall_clue", "E", 20, 2, "Mur est : le sceau, qui n'apparaît qu'une fois le duel gagné.", 2, 2)
r.w("pupitre_a", "terminal", "W", 24, 1, "Mur ouest : le premier pupitre du sceau.")
r.w("pupitre_b", "terminal", "E", 36, 1, "Mur est : le second pupitre du sceau, loin du premier.")
r.p("cachette_1", "secret", (25, 2, 17), "south", "Niche scellée au fond du caveau central.")
r.p("cachette_2", "secret", (6, 1, 10), "east", "Sous la dalle d'un sarcophage de l'ouest.")
r.p("cachette_3", "secret", (40, 1, 40), "west", "Dans un ossuaire du sud-est.")
r.step("os", "pickup_set", {"spots": 5, "filled": 4}, {}, [], [(f"spot_{i}", f"os_{i}") for i in range(1, 6)])
r.step("grille", "wiring", {"wires": 5, "colors": 4}, {"panel": "pad_grille"}, ["os"], [("scheme", "mur_grille"), ("scheme_text", "pierre_grille")])
r.step("caveau", "three_dials", {}, {"dials": "pad_caveau"}, ["grille"], [("code", "mur_caveau")])
r.step("boite", "pc_box", {"count": 8, "fixed": 3}, {"terminal": "pad_boite"}, [])
r.step("duel", "battle_puzzle", {"turns": 3, "mons": 2, "moves": 3}, {"arena": "arene"}, ["caveau", "boite"], [("reward", "mur_sceau", "solved")])
r.step("sarcophage", "twin_screens", {}, {"left": "pupitre_a", "right": "pupitre_b"}, ["duel"], [("left_code", "pupitre_a"), ("right_code", "pupitre_b")], exit=True)
r.dump()
print("ok")
