-- Trahison (betrayal game, 2026-11-16): the fixed plan of the complex, one row per saved version, per hub. History is kept
-- (an admin can compare or restore an older plan); the plan of a hub is its highest version. `data` holds zones, doors,
-- room slots, cabins and hiding spots in hub coordinates; `draft` marks an incomplete plan saved as work in progress.
CREATE TABLE IF NOT EXISTS trahison_layouts (
    hub        TEXT        NOT NULL,
    version    INTEGER     NOT NULL,
    draft      BOOLEAN     NOT NULL DEFAULT FALSE,
    data       JSONB       NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (hub, version)
);
