-- An event NPC's ambiance may name a stadium too ("<ambiance>|<stadium>", 2026-11-19): room for two ids.
ALTER TABLE event_npcs ALTER COLUMN ambiance TYPE VARCHAR(270);
