-- Trahison (betrayal game, 2026-11-16): one row per game, the whole state as JSON so a backend restart in the middle of
-- an evening resumes the game. Written at each phase change (Documentation/trahison/architecture.md section 4).
CREATE TABLE IF NOT EXISTS trahison_games (
    id         UUID        PRIMARY KEY,
    hub        TEXT        NOT NULL,
    state      JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS trahison_games_hub ON trahison_games (hub) WHERE ended_at IS NULL;
