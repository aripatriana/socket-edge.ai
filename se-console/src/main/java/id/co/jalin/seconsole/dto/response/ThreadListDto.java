package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Thread list response for the "Threads" table view.
 *
 * For performance we return minimal info per thread by default (no stack
 * traces). Individual stack can be fetched via /api/jvm/threads/{id} when
 * the user clicks a row.
 */
public record ThreadListDto(
        Instant timestamp,
        int totalCount,
        List<ThreadSummary> threads
) {
    public record ThreadSummary(
            long id,
            String name,
            String state,          // NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED
            boolean daemon,
            int priority,
            Long cpuTimeNs,        // null if cpu-time tracking disabled
            Long userTimeNs,       // null if cpu-time tracking disabled
            String lockName,       // non-null if blocked/waiting on monitor
            Long lockOwnerId,      // non-null if lock has owner
            String lockOwnerName,
            int stackDepth,        // total frames in stack (for heuristic)
            List<String> topFrames // first 3 stack frames as hint (always populated)
    ) {}
}
