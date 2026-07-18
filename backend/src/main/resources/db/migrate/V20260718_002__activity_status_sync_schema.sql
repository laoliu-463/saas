-- Activity list/status sync contract.
-- Keep this change in its own version so an already-applied role-aware
-- attribution migration never changes checksum after it reaches real-pre.

ALTER TABLE IF EXISTS public.colonel_activity
    ADD COLUMN IF NOT EXISTS activity_status_synced_at TIMESTAMP;
