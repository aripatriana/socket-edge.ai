// Types mirroring Java DTOs in id.co.jalin.seconsole.engine.dto.*
// Full rewrite (Chat 3e-3) to nested shape sourced from /socket/snapshot/channels.
// Metric fields now live under .metrics.latency / .metrics.pressureTps /
// .metrics.throughputTps with a 5-tuple distribution (avg/min/max/p90/p95).

export type SocketStatus =
  | 'DOWN'
  | 'STANDBY'
  | 'LISTEN'
  | 'WAIT'
  | 'ACTIVE'
  | 'ERROR';

export type ChannelState =
  | 'ACTIVE'
  | 'DEGRADED'
  | 'DOWN'
  | 'ERROR'
  | 'UNKNOWN';

export type SocketType = 'SERVER' | 'CLIENT';

// --- socket-level ---------------------------------------------------------

export interface SocketRuntime {
  state: SocketStatus;
  localHost: string;
  remoteHost: string;
  activeChannels: number;
  startTime: number;
  lastConnect: number;
  lastDisconnect: number;
}

export interface SocketQueue {
  msgIn: number;
  msgOut: number;
  depth: number;        // current in-flight / queue depth
  errCount: number;
  lastErr: number;
  lastMsg: number;
}

export interface LatencyDistribution {
  avg: number;
  min: number;
  max: number;
  p90: number;
  p95: number;
}

/** avg replaces the spec's `current` — same shape for pressure and throughput. */
export interface TpsDistribution {
  avg: number;
  min: number;
  max: number;
  p90: number;
  p95: number;
}

export interface SocketMetrics {
  latency: LatencyDistribution;
  pressureTps: TpsDistribution;
  throughputTps: TpsDistribution;
}

export interface SocketSummary {
  bindingId: string;
  socketId: string;
  name: string;
  type: SocketType;
  runtime: SocketRuntime;
  queue: SocketQueue;
  metrics: SocketMetrics;
}

// --- channel-level --------------------------------------------------------

export interface ChannelLatencyAggregate {
  /** Worst-of across sockets — one hot socket is the actionable signal. */
  maxAvg: number;
  maxMax: number;
  maxP95: number;
}

export interface ChannelTpsAggregate {
  /** Sum of per-socket {@link TpsDistribution.avg}. */
  totalAvg: number;
  /** Max of per-socket {@link TpsDistribution.avg}. */
  maxAvg: number;
  maxP95: number;
}

export interface ChannelAggregate {
  latency: ChannelLatencyAggregate;
  pressureTps: ChannelTpsAggregate;
  throughputTps: ChannelTpsAggregate;

  totalMsgIn: number;
  totalMsgOut: number;
  totalInFlight: number;
  totalErrCnt: number;
  maxLastErrMs: number;       // 0 if never errored
}

export interface ChannelSummary {
  name: string;
  aggregateState: ChannelState;
  socketsUp: number;
  socketsTotal: number;

  aggregate: ChannelAggregate;

  listenPort: number | null;
  clientStrategy: string | null;

  servers: SocketSummary[];
  clients: SocketSummary[];
}

export interface ChannelsResponse {
  channels: ChannelSummary[];
  reachable: boolean;
  lastUpdateMillis: number;
  lastError: string | null;
}

export interface EngineHealthResponse {
  reachable: boolean;
  status: string | null;
  role: string | null;
  mode: string | null;
  baseUrl: string;
  lastPollMs: number;
  lastError: string | null;
}

// --- history / charts -----------------------------------------------------

/**
 * One historical sample — projection of an engine_channel_socket_sample row.
 * Nested structure mirrors {@link SocketMetrics} so chart components can
 * reuse the same `metric.agg` access pattern for live and historical data.
 */
export interface HistorySample {
  t: number;
  state: SocketStatus;
  queueDepth: number;
  latency: LatencyDistribution;
  pressureTps: TpsDistribution;
  throughputTps: TpsDistribution;
}

export interface EndpointRef {
  bindingId: string;
  label: string;
  fullId: string;
  type: SocketType;
  status: SocketStatus;
}

export interface ChannelHistoryResponse {
  channelName: string;
  windowMs: number;
  now: number;
  endpoints: EndpointRef[];
  samplesByBindingId: Record<string, HistorySample[]>;
}

export type AggregationKey = 'avg' | 'min' | 'max' | 'p90' | 'p95';

// --- channel config (unchanged) -------------------------------------------

export interface PoolEndpoint {
  host: string;
  port: number;
  weight: number;
  priority: number;
  maxfails: number;
  failTimeout: number;
}

export interface ServerChannelConfig {
  listenHost: string;
  listenPort: number;
  strategy: string;
  pool: PoolEndpoint[];
}

export interface ClientChannelConfig {
  strategy: string;
  endpoints: PoolEndpoint[];
}

export interface ChannelConfigResponse {
  name: string;
  type: string;
  profiles: string[];
  unknownMti: string;
  server: ServerChannelConfig | null;
  client: ClientChannelConfig | null;
}

// --- control actions (unchanged) ------------------------------------------

export type ActionKey = 'start' | 'stop' | 'restart';
export type ActionScope = 'SOCKET' | 'CHANNEL';

export interface SocketActionResponse {
  success: boolean;
  action: ActionKey;
  scope: ActionScope;
  target: string;
  durationMs: number;
  message: string;
}
