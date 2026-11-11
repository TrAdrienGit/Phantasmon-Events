-- Tournaments (plan-tournois.md §8, 2026-11-11): the check-in has its own dates; at its opening the check-in opens by
-- itself, at its closing the tournament starts with those present. An admin may still trigger each step by hand.
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS check_in_opens_at TIMESTAMPTZ;
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS check_in_closes_at TIMESTAMPTZ;
