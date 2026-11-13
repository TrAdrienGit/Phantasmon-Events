-- Tournaments (plan-tournois.md §10, 2026-11-12): a readable name ("Weekly OU #1"), shown everywhere; the lower-case
-- name stays the tournament's id (URL, commands), derived from it at creation. Null for the older tournaments: their id
-- is shown instead.
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS display_name VARCHAR(48);
