import { useState } from 'react';
import type { DeadlockInfo } from '../../api/jvm.types';

/**
 * Red banner shown when the JVM has deadlocked threads.
 * Click "View details" to expand the stack trace modal.
 *
 * Renders nothing when deadlock.count === 0 (the common case).
 */
export function DeadlockBanner({ deadlock }: { deadlock: DeadlockInfo }) {
  const [expanded, setExpanded] = useState(false);

  if (deadlock.count === 0) return null;

  return (
    <>
      <div
        role="alert"
        className="flex items-start gap-3 px-4 py-3 rounded-md border border-destructive bg-destructive/10 text-destructive mb-4"
      >
        <span className="text-[18px] leading-none mt-0.5">⚠</span>
        <div className="flex-1 min-w-0">
          <div className="font-semibold text-[13px]">
            {deadlock.count} thread{deadlock.count > 1 ? 's' : ''} in deadlock
          </div>
          <div className="text-[12px] mt-1 text-destructive/90 leading-relaxed">
            {firstLineSummary(deadlock)}
          </div>
        </div>
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="text-[12px] font-semibold underline hover:no-underline whitespace-nowrap"
        >
          View details
        </button>
      </div>

      {expanded && (
        <DeadlockModal deadlock={deadlock} onClose={() => setExpanded(false)} />
      )}
    </>
  );
}

/**
 * Build a short single-line description of the first deadlocked thread,
 * usable as the banner sub-text.
 */
function firstLineSummary(deadlock: DeadlockInfo): string {
  const first = deadlock.threads[0];
  if (!first) return 'Use "View details" for stack traces.';
  const waiter = first.threadName ?? `#${first.threadId}`;
  const holder = first.lockOwnerName ?? `thread #${first.lockOwnerId ?? '?'}`;
  const lock = first.lockName ?? 'lock';
  return `"${waiter}" is waiting for ${lock}, held by "${holder}".`;
}

function DeadlockModal({ deadlock, onClose }: { deadlock: DeadlockInfo; onClose: () => void }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-card border border-border rounded-md shadow-xl max-w-3xl w-full max-h-[80vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-3 border-b border-border">
          <div className="flex items-center gap-2">
            <span className="text-destructive">⚠</span>
            <span className="font-semibold text-[14px] text-foreground">
              Deadlocked threads ({deadlock.count})
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground text-[14px] cursor-pointer"
            aria-label="Close"
          >
            ✕
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {deadlock.threads.map((thread, i) => (
            <div
              key={thread.threadId}
              className={i > 0 ? 'pt-4 border-t border-border' : ''}
            >
              <div className="font-mono text-[12px] text-foreground font-semibold mb-1">
                "{thread.threadName}" #{thread.threadId} [{thread.threadState}]
              </div>
              {thread.lockName && (
                <div className="text-[11px] text-muted-foreground mb-2">
                  Waiting on: <span className="font-mono text-foreground">{thread.lockName}</span>
                  {thread.lockOwnerName && (
                    <>
                      {' '}· Held by:{' '}
                      <span className="font-mono text-foreground">
                        "{thread.lockOwnerName}" #{thread.lockOwnerId}
                      </span>
                    </>
                  )}
                </div>
              )}
              {thread.stackTrace.length > 0 && (
                <pre className="font-mono text-[10.5px] text-foreground bg-muted rounded px-3 py-2 overflow-x-auto leading-relaxed">
                  {thread.stackTrace.map((line) => `\tat ${line}\n`).join('')}
                </pre>
              )}
            </div>
          ))}
        </div>
        <div className="px-5 py-3 border-t border-border text-[11px] text-muted-foreground">
          Restart the affected service or investigate the locks involved to
          recover.
        </div>
      </div>
    </div>
  );
}
