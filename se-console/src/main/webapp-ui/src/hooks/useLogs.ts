import { useQuery } from '@tanstack/react-query';
import { logsApi } from '../api/logs';
import type {
  LogTailResponse,
  PollIntervalKey,
  LineCount,
} from '../api/logs.types';
import { POLL_INTERVAL_MS } from '../api/logs.types';

/**
 * Tail a log file with configurable polling.
 *
 * <p>When {@code interval} is {@code 'off'}, the query is fetched once and
 * never refetched — the UI surfaces a "Refresh" button for manual updates.
 */
export function useLogs(fileName: string, lines: LineCount, interval: PollIntervalKey) {
  const refetchInterval = POLL_INTERVAL_MS[interval];

  return useQuery<LogTailResponse>({
    queryKey: ['logs', fileName, lines],
    queryFn: () => logsApi.tail(fileName, lines),
    refetchInterval: refetchInterval === false ? false : refetchInterval,
    refetchOnWindowFocus: false,
    // Keep previous data visible while the next poll is in-flight — prevents
    // the viewer from flashing empty between ticks.
    placeholderData: (prev) => prev,
    staleTime: 0,
  });
}
