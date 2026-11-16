-- Trahison (betrayal game, 2026-11-16): the room bank. One row per room: `data` is its definition (slots, chain of steps,
-- hints) as JSON; the blocks are a file on the backend (hub schematics dir, trahison_rooms/<id>/room.bin), not in the
-- database. `version` counts the saves of the room.
CREATE TABLE IF NOT EXISTS trahison_rooms (
    id         TEXT        PRIMARY KEY,
    version    INTEGER     NOT NULL,
    data       JSONB       NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
