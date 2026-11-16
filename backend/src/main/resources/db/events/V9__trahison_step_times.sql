-- Trahison (betrayal game, 2026-11-16): the measured time of every step really solved by a group (T3.6). One row per
-- solved step: which room and version, the mechanism and its parameters, how long the step stayed open before it was
-- solved, how many help texts the group had been given, and the size of the group. The medians per mechanism and per
-- room come from here; a room's displayed expected time uses them once a step has at least 3 measures.
CREATE TABLE IF NOT EXISTS trahison_step_times (
    id           BIGSERIAL   PRIMARY KEY,
    room_id      TEXT        NOT NULL,
    room_version INTEGER     NOT NULL,
    step_id      TEXT        NOT NULL,
    mechanism    TEXT        NOT NULL,
    params       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    seconds      INTEGER     NOT NULL,
    hints_given  SMALLINT    NOT NULL DEFAULT 0,
    group_size   SMALLINT    NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_trahison_step_times_room ON trahison_step_times (room_id, step_id);
CREATE INDEX IF NOT EXISTS idx_trahison_step_times_mechanism ON trahison_step_times (mechanism);
