-- Phantasmon Events' mirror hubs (D-38, D-41): the anchor rules of a MIRROR hub, which the core no longer knows.
-- One SOURCE anchor per hub (the server zone copied), one COPY anchor per admin and hub. Before V25 the core held
-- the same rules (V15) with a mirror-specific index.

CREATE UNIQUE INDEX IF NOT EXISTS uq_events_mirror_source ON hub_anchors(hub_uuid) WHERE role = 'SOURCE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_events_mirror_copy ON hub_anchors(owner_uuid, hub_uuid) WHERE role = 'COPY';
