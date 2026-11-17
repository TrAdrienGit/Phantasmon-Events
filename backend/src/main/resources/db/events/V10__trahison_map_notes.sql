-- Trahison (T11.4, 2026-11-17): what a person draws and writes on the map during a game. One row per game and account
-- (a player, a ghost, a game master or a spectator); only the owner reads or writes his own row while the game runs, and
-- at the end of the game everybody may read everybody's (D15). The strokes are kept per floor, in hub coordinates.
CREATE TABLE IF NOT EXISTS trahison_map_notes (
    game_id    UUID        NOT NULL,
    account    UUID        NOT NULL,
    strokes    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    notes      TEXT        NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, account)
);
