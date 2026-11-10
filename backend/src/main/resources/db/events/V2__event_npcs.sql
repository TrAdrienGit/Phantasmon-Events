-- Phantasmon Events' own migrations (D-41, TODO-35), history in flyway_schema_history_events.
-- Moved from the core's V22-V24: on a database where the core already created these tables, every statement
-- is a no-op (IF NOT EXISTS); on a new database, it creates them.

-- Phantasmon Events: events an admin opens and closes, and their trainer NPCs (Adrien 2026-11-02). An NPC stands at a
-- place of one server and dimension; players can see and challenge it only while its event is open. Its team is a
-- copy of Pokémon (the same JSON as a battle's teams), played by the AI it is given.
CREATE TABLE IF NOT EXISTS event_groups (
    name       VARCHAR(32) PRIMARY KEY,
    open       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS event_npcs (
    uuid               UUID PRIMARY KEY,
    event_name         VARCHAR(32)  NOT NULL REFERENCES event_groups(name) ON DELETE CASCADE,
    name               VARCHAR(32)  NOT NULL,
    skin               VARCHAR(16),
    server_fingerprint VARCHAR(128) NOT NULL,
    dimension          VARCHAR(128) NOT NULL,
    x                  DOUBLE PRECISION NOT NULL,
    y                  DOUBLE PRECISION NOT NULL,
    z                  DOUBLE PRECISION NOT NULL,
    yaw                REAL         NOT NULL DEFAULT 0,
    team               JSONB        NOT NULL DEFAULT '[]',
    ai                 VARCHAR(16)  NOT NULL DEFAULT 'normal',
    format_id          VARCHAR(64)  NOT NULL DEFAULT 'free',
    created_by         UUID         NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS event_npcs_place ON event_npcs (server_fingerprint, dimension);

-- An event NPC may belong to a hub (Adrien 2026-11-03): then x, y, z, yaw are its place IN the hub (hub coordinates:
-- centre 0, 0, y from the anchor's floor) and every client shows it in each of that hub's zones it has (the source,
-- the copies, the ordinary anchors). server_fingerprint and dimension keep where it was posed.
ALTER TABLE event_npcs ADD COLUMN IF NOT EXISTS hub_uuid UUID REFERENCES hubs(uuid) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS event_npcs_hub ON event_npcs (hub_uuid);

-- Event NPC: its AI explains each decision in the chat of the player battling it (debugging the Expert AI, 2026-11-03).
ALTER TABLE event_npcs ADD COLUMN IF NOT EXISTS ai_debug BOOLEAN NOT NULL DEFAULT FALSE;
