-- ============================================================================
-- V3 — Engine channel snapshot history (parent-child)
--
-- Chat 3e-3 full rewrite. Replaces the in-memory ring-buffer history
-- service with H2-persisted time-series. Architecture per
-- snapshot_seconsole.png:
--
--   SE-Core /socket/snapshot/channels
--     → EngineChannelSnapshotService (2s poll)
--     → AtomicReference cache  +  H2 parent/child rows
--
-- Two tables:
--   engine_channel_snapshot        — one row per poll (header)
--   engine_channel_socket_sample   — one row per socket per poll (detail)
--
-- Cascade rule: deleting a header row drops all its child sample rows
-- in the same statement, which lets the retention pruner work on headers
-- alone (much smaller working set).
--
-- H2 NOTE: AUTO_INCREMENT + BIGINT IDENTITY + FOREIGN KEY ... ON DELETE
-- CASCADE are all supported. Index ordering (DESC) is honored on 2.x.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Parent: one row per snapshot poll
-- ---------------------------------------------------------------------------
CREATE TABLE engine_channel_snapshot (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    captured_at             TIMESTAMP     NOT NULL,
    snapshot_id             VARCHAR(64),
    capture_duration_ms     BIGINT        NOT NULL DEFAULT 0,

    -- aggregate (engine-wide) counters
    socket_count            INT           NOT NULL,
    sockets_up              INT           NOT NULL,
    sockets_down            INT           NOT NULL,
    total_active_channels   INT           NOT NULL DEFAULT 0,
    total_msg_in            BIGINT        NOT NULL DEFAULT 0,
    total_msg_out           BIGINT        NOT NULL DEFAULT 0,
    total_queue_depth       BIGINT        NOT NULL DEFAULT 0,
    total_err_count         BIGINT        NOT NULL DEFAULT 0,
    avg_pressure_tps        BIGINT        NOT NULL DEFAULT 0,
    avg_throughput_tps      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_ecs_captured_at
    ON engine_channel_snapshot (captured_at DESC);

-- ---------------------------------------------------------------------------
-- Child: one row per (snapshot, socket) — the per-socket time-series
-- ---------------------------------------------------------------------------
CREATE TABLE engine_channel_socket_sample (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    snapshot_header_id      BIGINT        NOT NULL,
    captured_at             TIMESTAMP     NOT NULL,      -- denormalised from header for fast range scans

    -- identity
    hash_id                 VARCHAR(16)   NOT NULL,
    socket_id               VARCHAR(128)  NOT NULL,
    channel_name            VARCHAR(64)   NOT NULL,
    socket_type             VARCHAR(8)    NOT NULL,      -- CLIENT | SERVER

    -- runtime
    state                   VARCHAR(16)   NOT NULL,
    local_host              VARCHAR(64),
    remote_host             VARCHAR(256),
    active_channels         INT           NOT NULL DEFAULT 0,
    start_time              BIGINT        NOT NULL DEFAULT 0,
    last_connect            BIGINT        NOT NULL DEFAULT 0,
    last_disconnect         BIGINT        NOT NULL DEFAULT 0,

    -- queue
    msg_in                  BIGINT        NOT NULL DEFAULT 0,
    msg_out                 BIGINT        NOT NULL DEFAULT 0,
    queue_depth             BIGINT        NOT NULL DEFAULT 0,
    err_count               BIGINT        NOT NULL DEFAULT 0,
    last_err                BIGINT        NOT NULL DEFAULT 0,
    last_msg                BIGINT        NOT NULL DEFAULT 0,

    -- latency (ns)
    lat_avg_ns              BIGINT        NOT NULL DEFAULT 0,
    lat_min_ns              BIGINT        NOT NULL DEFAULT 0,
    lat_max_ns              BIGINT        NOT NULL DEFAULT 0,
    lat_p90_ns              BIGINT        NOT NULL DEFAULT 0,
    lat_p95_ns              BIGINT        NOT NULL DEFAULT 0,

    -- pressure TPS (distribution)
    pressure_avg            BIGINT        NOT NULL DEFAULT 0,
    pressure_min            BIGINT        NOT NULL DEFAULT 0,
    pressure_max            BIGINT        NOT NULL DEFAULT 0,
    pressure_p90            BIGINT        NOT NULL DEFAULT 0,
    pressure_p95            BIGINT        NOT NULL DEFAULT 0,

    -- throughput TPS (distribution)
    throughput_avg          BIGINT        NOT NULL DEFAULT 0,
    throughput_min          BIGINT        NOT NULL DEFAULT 0,
    throughput_max          BIGINT        NOT NULL DEFAULT 0,
    throughput_p90          BIGINT        NOT NULL DEFAULT 0,
    throughput_p95          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT fk_ecss_header
        FOREIGN KEY (snapshot_header_id)
        REFERENCES engine_channel_snapshot(id)
        ON DELETE CASCADE
);

-- Primary query pattern — "chart hashId X for last N minutes":
CREATE INDEX idx_ecss_hash_time
    ON engine_channel_socket_sample (hash_id, captured_at DESC);

-- Secondary — support the FK lookup/cascade efficiently:
CREATE INDEX idx_ecss_header
    ON engine_channel_socket_sample (snapshot_header_id);
