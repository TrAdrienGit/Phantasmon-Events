-- The Showtime intro and ambiance an event NPC's battles impose (2026-11-18); null: the player's pick.
ALTER TABLE event_npcs ADD COLUMN intro VARCHAR(130);
ALTER TABLE event_npcs ADD COLUMN ambiance VARCHAR(130);
