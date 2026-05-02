-- ============================================================================
-- V2 — Engine JVM snapshot history
--
-- Adds the time-series table that stores every poll of SE-Core's
-- /socket/snapshot/jvm endpoint. Populated by EngineJvmHistoryService from
-- EngineJvmSnapshotService's scheduled poll (see architecture in
-- snapshot_seconsole.png — Console-side SnapshotPoller → cache → H2 sink).
--
-- Retention is enforced by EngineJvmHistoryService.prune() on a schedule;
-- no DB-level job. Default cut-off is 7 days (configurable).
--
-- Variable-cardinality lists (pools, gc, buffers) are stored as JSON CLOBs
-- rather than child tables — they are always queried in one shot together
-- with the parent row, and avoiding the child-table join keeps inserts at
-- the 2-second poll cadence cheap.
--
-- H2 compatibility: BIGINT IDENTITY + CLOB + TIMESTAMP are all supported.
-- ============================================================================

CREATE TABLE engine_jvm_snapshot (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    captured_at             TIMESTAMP     NOT NULL,
    snapshot_id             VARCHAR(64),

    -- engineProcess
    pid                     BIGINT,
    uptime_ms               BIGINT        NOT NULL,
    java_version            VARCHAR(64),
    vm_name                 VARCHAR(128),

    -- heap area
    heap_used               BIGINT        NOT NULL,
    heap_committed          BIGINT        NOT NULL,
    heap_max                BIGINT        NOT NULL,
    heap_init               BIGINT        NOT NULL,

    -- non-heap area
    nonheap_used            BIGINT        NOT NULL,
    nonheap_committed       BIGINT        NOT NULL,
    nonheap_max             BIGINT        NOT NULL,
    nonheap_init            BIGINT        NOT NULL,

    -- threads (scalar counters; byState kept as nullable columns so chart
    -- queries can aggregate them without parsing JSON)
    threads_current         INT           NOT NULL,
    threads_daemon          INT           NOT NULL,
    threads_peak            INT           NOT NULL,
    threads_total_started   BIGINT        NOT NULL,
    threads_deadlocked      INT           NOT NULL DEFAULT 0,
    threads_runnable        INT,
    threads_blocked         INT,
    threads_waiting         INT,
    threads_timed_waiting   INT,

    -- classes
    classes_loaded          INT           NOT NULL,
    classes_total_loaded    BIGINT        NOT NULL,
    classes_unloaded        BIGINT        NOT NULL,

    -- GC aggregates across all collectors
    gc_total_count          BIGINT        NOT NULL,
    gc_total_time_ms        BIGINT        NOT NULL,

    -- Variable-cardinality lists serialized as JSON
    pools_json              CLOB,
    gc_json                 CLOB,
    buffers_json            CLOB
);

-- Primary query pattern: "latest N rows" or "rows since T" — both served by
-- a descending index on captured_at.
CREATE INDEX idx_engine_jvm_snapshot_captured_at
    ON engine_jvm_snapshot (captured_at DESC);
