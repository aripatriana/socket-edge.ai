-- ============================================================================
-- V5 — Rename hash_id → binding_id in engine_channel_socket_sample
--
-- Aligns the DB column with the renamed Java field (bindingId) and proto
-- field (binding_id). The H2 RENAME COLUMN syntax is used since this is a
-- development / embedded database.
-- ============================================================================

ALTER TABLE engine_channel_socket_sample
    RENAME COLUMN hash_id TO binding_id;

-- Recreate the range-scan index under the new column name.
DROP INDEX IF EXISTS idx_ecss_hash_time;

CREATE INDEX idx_ecss_binding_time
    ON engine_channel_socket_sample (binding_id, captured_at DESC);
