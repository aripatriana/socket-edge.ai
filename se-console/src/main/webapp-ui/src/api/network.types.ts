export interface NetworkMetrics {
  timestamp: string;
  interfaces: NetworkInterfaceInfo[];
  tcpStates: TcpStateCounts;
  tcpQuality: TcpQualityCounters;
  listeningPorts: ListeningPort[];
}

export interface NetworkInterfaceInfo {
  name: string;
  displayName: string;
  macAddress: string;
  ipv4Addresses: string[];
  ipv6Addresses: string[];
  speedBitsPerSecond: number;   // -1 if unknown
  mtu: number;
  up: boolean;
  bytesRecv: number;            // cumulative
  bytesSent: number;
  packetsRecv: number;
  packetsSent: number;
  inErrors: number;
  outErrors: number;
  inDrops: number;
  outDrops: number;
}

export interface TcpStateCounts {
  total: number;
  established: number;
  timeWait: number;
  closeWait: number;
  listen: number;
  synSent: number;
  synRecv: number;
  finWait1: number;
  finWait2: number;
  lastAck: number;
  closing: number;
  other: number;
  rawByState: Record<string, number>;
}

/**
 * All fields cumulative since boot. Frontend computes rates from
 * consecutive snapshots using the same delta pattern as Chat 3b+ GcRateChart.
 *
 * All fields nullable — on non-Linux platforms these are not parseable.
 */
export interface TcpQualityCounters {
  retransSegs: number | null;
  outSegs: number | null;
  outResets: number | null;
  inErrs: number | null;
  attemptFails: number | null;
  estabResets: number | null;
  currEstab: number | null;
  syncookiesSent: number | null;
  listenDrops: number | null;
  listenOverflows: number | null;
}

/**
 * Listening port row. Chat 3c will introduce an extended ListeningPortWithChannel
 * type that merges this with engine JMX data; consumers that only need
 * OS-level info continue to use this type.
 */
export interface ListeningPort {
  localAddress: string;
  port: number;
  protocol: 'TCP' | 'TCP6' | string;
  establishedCount: number;
  pid: number | null;
}

// --- /api/console/network/connections ------------------------------------

export interface NetworkConnections {
  timestamp: string;
  totalCount: number;
  returnedCount: number;
  truncated: boolean;
  connections: TcpConnection[];
}

export interface TcpConnection {
  localAddress: string;
  localPort: number;
  remoteAddress: string;
  remotePort: number;
  state: string;
  protocol: 'TCP' | 'TCP6' | string;
  pid: number | null;
}

/**
 * One row from /api/console/network/history — maps directly to
 * ConsoleNetworkSnapshotEntity. Scalar TCP state counts are flat;
 * per-NIC detail and per-port detail are JSON-serialized lists.
 */
export interface NetworkSnapshotRow {
  id: number;
  capturedAt: string;

  tcpTotal: number;
  tcpEstablished: number;
  tcpTimeWait: number;
  tcpCloseWait: number;
  tcpListen: number;
  tcpSynSent: number;
  tcpSynRecv: number;
  tcpFinWait1: number;
  tcpFinWait2: number;
  tcpLastAck: number;
  tcpClosing: number;
  tcpOther: number;

  tcpRetransSegs: number | null;
  tcpOutSegs: number | null;
  tcpOutResets: number | null;
  tcpInErrs: number | null;
  tcpAttemptFails: number | null;
  tcpEstabResets: number | null;
  tcpCurrEstab: number | null;
  tcpSyncookiesSent: number | null;
  tcpListenDrops: number | null;
  tcpListenOverflows: number | null;

  interfacesJson: string | null;
  listeningPortsJson: string | null;
  tcpStatesRawJson: string | null;
}
