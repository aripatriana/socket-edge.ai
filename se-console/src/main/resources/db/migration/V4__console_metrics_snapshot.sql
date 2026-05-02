-- ============================================================================
-- SE-Console — console-side metrics snapshot tables (V4)
--
-- Three time-series tables, one per monitoring domain:
--   console_system_snapshot       OS-level metrics (CPU, memory, disks, FDs)
--   console_network_snapshot      Network interfaces, TCP states, listening ports
--   console_jvm_internal_snapshot Console's own JVM (mirrors engine_jvm_snapshot)
--
-- Design — same pattern as V2/V3:
--   * Flat columns for scalars queried often in charts (cpu_system_pct, etc.)
--   * CLOB/JSON columns for variable-cardinality lists (disks, interfaces,
--     memory pools) — avoids either a dedicated child table per list or a
--     column explosion from denormalising every disk/NIC/pool into the header.
--   * captured_at indexed for time-range scans; retention pruner filters by it.
--
-- Retention: enforced application-side by scheduled prune() in each
-- *HistoryService (default 7 days, configurable via
-- seconsole.monitoring.<domain>.history.retention-days).
-- ============================================================================


-- ---------------------------------------------------------------------------
-- console_system_snapshot
-- One row per poll tick. Disks go into disks_json because the count varies
-- across deployments (1-20 mounts). Same reasoning as pools_json in V2.
-- ---------------------------------------------------------------------------
CREATE TABLE console_system_snapshot (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    captured_at             TIMESTAMP     NOT NULL,

    -- CPU
    cpu_system_pct          DOUBLE,       -- 0..100, nullable (Windows load avg unavail)
    cpu_process_pct         DOUBLE,
    load_avg_1m             DOUBLE,
    load_avg_5m             DOUBLE,
    load_avg_15m            DOUBLE,
    cpu_logical_cores       INT,
    cpu_physical_cores      INT,
    cpu_model               VARCHAR(256),

    -- Memory
    mem_total_bytes         BIGINT        NOT NULL,
    mem_available_bytes     BIGINT        NOT NULL,
    mem_used_bytes          BIGINT        NOT NULL,
    mem_used_pct            DOUBLE,
    swap_total_bytes        BIGINT        NOT NULL,
    swap_used_bytes         BIGINT        NOT NULL,
    mem_cached_bytes        BIGINT,       -- Linux only
    mem_buffers_bytes       BIGINT,       -- Linux only

    -- File descriptors (OpenJDK only — null on Windows)
    fd_open                 BIGINT,
    fd_max                  BIGINT,
    fd_used_pct             DOUBLE,

    -- Host
    host_name               VARCHAR(128),
    os_name                 VARCHAR(64),
    os_version              VARCHAR(128),
    os_arch                 VARCHAR(32),
    host_uptime_seconds     BIGINT,
    host_boot_time          TIMESTAMP,

    -- Process (Console JVM itself)
    process_pid             INT,
    process_name            VARCHAR(128),
    process_user            VARCHAR(128),
    process_working_dir     VARCHAR(512),
    process_start_time      TIMESTAMP,
    process_uptime_seconds  BIGINT,
    process_rss_bytes       BIGINT,
    process_vmem_bytes      BIGINT,
    process_thread_count    INT,
    process_open_files      BIGINT,

    -- Variable-cardinality lists (disks — one entry per mount)
    disks_json              CLOB
);

CREATE INDEX idx_console_system_snapshot_captured_at
    ON console_system_snapshot(captured_at DESC);


-- ---------------------------------------------------------------------------
-- console_network_snapshot
-- Interfaces and listening ports are variable-cardinality → JSON.
-- TCP state counts and quality counters are flat because dashboards chart
-- them directly (TCP established over time, retrans rate, etc.).
-- ---------------------------------------------------------------------------
CREATE TABLE console_network_snapshot (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    captured_at             TIMESTAMP     NOT NULL,

    -- TCP state counts (flat — charted directly)
    tcp_total               INT           NOT NULL DEFAULT 0,
    tcp_established         INT           NOT NULL DEFAULT 0,
    tcp_time_wait           INT           NOT NULL DEFAULT 0,
    tcp_close_wait          INT           NOT NULL DEFAULT 0,
    tcp_listen              INT           NOT NULL DEFAULT 0,
    tcp_syn_sent            INT           NOT NULL DEFAULT 0,
    tcp_syn_recv            INT           NOT NULL DEFAULT 0,
    tcp_fin_wait_1          INT           NOT NULL DEFAULT 0,
    tcp_fin_wait_2          INT           NOT NULL DEFAULT 0,
    tcp_last_ack            INT           NOT NULL DEFAULT 0,
    tcp_closing             INT           NOT NULL DEFAULT 0,
    tcp_other               INT           NOT NULL DEFAULT 0,

    -- TCP quality counters (cumulative since boot — rates computed from deltas)
    tcp_retrans_segs        BIGINT,
    tcp_out_segs            BIGINT,
    tcp_out_resets          BIGINT,
    tcp_in_errs             BIGINT,
    tcp_attempt_fails       BIGINT,
    tcp_estab_resets        BIGINT,
    tcp_curr_estab          BIGINT,
    tcp_syncookies_sent     BIGINT,
    tcp_listen_drops        BIGINT,
    tcp_listen_overflows    BIGINT,

    -- Variable-cardinality lists
    interfaces_json         CLOB,         -- per-NIC: bytes/pkts/errs cumulative
    listening_ports_json    CLOB,         -- bound ports with establishedCount
    tcp_states_raw_json     CLOB          -- full breakdown by state name
);

CREATE INDEX idx_console_network_snapshot_captured_at
    ON console_network_snapshot(captured_at DESC);


-- ---------------------------------------------------------------------------
-- console_jvm_internal_snapshot
-- Mirrors engine_jvm_snapshot (V2) column-for-column — the Console's own JVM
-- has the same shape as SE-Core's JVM. The monitor/JVM tab can re-use the
-- same renderer component for both data sources.
-- ---------------------------------------------------------------------------
CREATE TABLE console_jvm_internal_snapshot (
    id                      BIGINT        AUTO_INCREMENT PRIMARY KEY,
    captured_at             TIMESTAMP     NOT NULL,

    -- Runtime
    vm_name                 VARCHAR(128),
    vm_vendor               VARCHAR(128),
    vm_version              VARCHAR(64),
    spec_version            VARCHAR(16),
    uptime_ms               BIGINT        NOT NULL DEFAULT 0,
    start_time              BIGINT        NOT NULL DEFAULT 0,

    -- Heap
    heap_used               BIGINT        NOT NULL,
    heap_committed          BIGINT        NOT NULL,
    heap_max                BIGINT        NOT NULL,
    heap_init               BIGINT        NOT NULL,

    -- Non-heap
    nonheap_used            BIGINT        NOT NULL,
    nonheap_committed       BIGINT        NOT NULL,
    nonheap_max             BIGINT        NOT NULL,
    nonheap_init            BIGINT        NOT NULL,

    -- Threads (scalars — variable-state breakdown in threads_by_state_json)
    threads_live            INT           NOT NULL,
    threads_daemon          INT           NOT NULL,
    threads_peak            INT           NOT NULL,
    threads_total_started   BIGINT        NOT NULL,
    threads_runnable        INT,
    threads_blocked         INT,
    threads_waiting         INT,
    threads_timed_waiting   INT,

    -- Classes
    classes_loaded          INT           NOT NULL,
    classes_total_loaded    BIGINT        NOT NULL,
    classes_unloaded        BIGINT        NOT NULL,

    -- Deadlock summary (threads detail in deadlock_json)
    deadlock_count          INT           NOT NULL DEFAULT 0,

    -- GC aggregate (per-collector detail in gc_json)
    gc_total_count          BIGINT        NOT NULL DEFAULT 0,
    gc_total_time_ms        BIGINT        NOT NULL DEFAULT 0,

    -- Variable-cardinality JSON blobs
    pools_json              CLOB,         -- memory pools (Eden, Survivor, Old, Metaspace, ...)
    gc_json                 CLOB,         -- per-collector count/time/pool-names
    deadlock_json           CLOB          -- deadlocked threads detail (usually empty)
);

CREATE INDEX idx_console_jvm_internal_snapshot_captured_at
    ON console_jvm_internal_snapshot(captured_at DESC);
