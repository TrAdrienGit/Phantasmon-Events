-- The Showtime ambiance laid on a hub (2026-11-19): everyone in the hub sees it, battle or not. One per hub; no row: none.
-- Keyed by the hub's uuid (a rename keeps it); a row whose hub is gone is ignored, then removed on the next change.
CREATE TABLE IF NOT EXISTS event_hub_ambiances (
    hub_uuid   UUID         PRIMARY KEY,
    ambiance   VARCHAR(130) NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
