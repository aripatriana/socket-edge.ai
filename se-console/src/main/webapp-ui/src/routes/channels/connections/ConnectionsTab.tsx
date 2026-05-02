import { useState } from 'react';
import type {
  ChannelSummary,
  SocketSummary,
  ActionKey,
  SocketActionResponse,
} from '../../../api/channels.types';
import { SocketTable } from '../../../components/channels/connections/SocketTable';
import { ChannelActionBar } from '../../../components/channels/connections/ChannelActionBar';
import {
  ActionConfirmDialog,
  type ConfirmRequest,
} from '../../../components/channels/connections/ActionConfirmDialog';
import {
  ActionBanner,
  type BannerState,
} from '../../../components/channels/connections/ActionBanner';
import { useChannelAction, useSocketAction } from '../../../hooks/useSocketAction';
import { ApiError } from '../../../api/client';

interface Props {
  channelName: string;
  channel: ChannelSummary;
}

/**
 * Connections tab. Top-to-bottom:
 *   [ inline banner, if any ]
 *   [ channel-level action bar — Start all / Stop all / Restart all ]
 *   [ socket table with per-row ⋮ menu ]
 *   [ confirm dialog overlay, when pending ]
 */
export function ConnectionsTab({ channelName, channel }: Props) {
  const [pending, setPending] = useState<ConfirmRequest | null>(null);
  const [pendingKind, setPendingKind] = useState<'channel' | 'socket' | null>(null);
  const [pendingHashId, setPendingHashId] = useState<string | null>(null);
  const [banner, setBanner] = useState<BannerState | null>(null);

  const channelMut = useChannelAction(channelName);
  const socketMut = useSocketAction(channelName);

  const busy = channelMut.isPending || socketMut.isPending;
  const busyChannelAction = channelMut.isPending ? (channelMut.variables as ActionKey) : null;
  const busyHashId = socketMut.isPending ? socketMut.variables?.hashId ?? null : null;

  const requestChannelAction = (action: ActionKey) => {
    setPending({
      action,
      scope: 'CHANNEL',
      targetLabel: channelName,
      affectedCount: channel.socketsTotal,
      currentStatus: `${channel.aggregateState} · ${channel.socketsUp}/${channel.socketsTotal} up`,
      consequence: action === 'stop' && channel.aggregate.totalInFlight > 0
        ? `${channel.aggregate.totalInFlight} in-flight message${channel.aggregate.totalInFlight === 1 ? '' : 's'} may be interrupted`
        : undefined,
    });
    setPendingKind('channel');
    setPendingHashId(null);
  };

  const requestSocketAction = (socket: SocketSummary, action: ActionKey) => {
    setPending({
      action,
      scope: 'SOCKET',
      targetLabel: socket.socketId,
      affectedCount: 1,
      currentStatus: `${socket.runtime.state} · ${socket.runtime.activeChannels} active conn${socket.runtime.activeChannels === 1 ? '' : 's'}`,
      consequence: action === 'stop' && socket.queue.depth > 0
        ? `${socket.queue.depth} in-flight message${socket.queue.depth === 1 ? '' : 's'} may be interrupted`
        : undefined,
    });
    setPendingKind('socket');
    setPendingHashId(socket.hashId);
  };

  const onCancel = () => {
    setPending(null);
    setPendingKind(null);
    setPendingHashId(null);
  };

  const onConfirm = async () => {
    if (!pending || !pendingKind) return;
    const req = pending;

    try {
      let resp: SocketActionResponse;
      if (pendingKind === 'channel') {
        resp = await channelMut.mutateAsync(req.action);
      } else if (pendingHashId) {
        resp = await socketMut.mutateAsync({ hashId: pendingHashId, action: req.action });
      } else {
        return;
      }

      setBanner({
        kind: resp.success ? 'success' : 'error',
        message: resp.success
          ? `${req.action} executed on ${req.targetLabel}`
          : `Action failed: ${resp.message}`,
        response: resp,
      });
    } catch (err) {
      const message = extractErrorMessage(err);
      setBanner({
        kind: 'error',
        message: `Action failed: ${message}`,
      });
    } finally {
      setPending(null);
      setPendingKind(null);
      setPendingHashId(null);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      {banner && (
        <ActionBanner state={banner} onDismiss={() => setBanner(null)} />
      )}

      <div className="flex items-center justify-between flex-wrap gap-3">
        <ChannelActionBar
          disabled={busy}
          busyAction={busyChannelAction}
          onAction={requestChannelAction}
        />
        <span className="text-[11px] text-muted-foreground font-mono">
          {channel.socketsTotal} socket{channel.socketsTotal === 1 ? '' : 's'} ·{' '}
          <span className={channel.socketsUp === channel.socketsTotal ? 'text-emerald-600' : 'text-amber-600'}>
            {channel.socketsUp} up
          </span>
        </span>
      </div>

      <SocketTable
        channel={channel}
        onSocketAction={requestSocketAction}
        busyHashId={busyHashId}
      />

      <ActionConfirmDialog
        request={pending}
        busy={busy}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------

function extractErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    const body = err.details as Record<string, unknown> | undefined;
    if (body?.message && typeof body.message === 'string') return body.message;
    return err.message;
  }
  if (err instanceof Error) return err.message;
  return String(err);
}
