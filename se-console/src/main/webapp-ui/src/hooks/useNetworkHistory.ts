import { useQuery } from '@tanstack/react-query';
import { networkApi } from '../api/network';
import type { NetworkSnapshotRow } from '../api/network.types';

export function useNetworkHistory(from: string | null, to: string | null) {
  return useQuery<NetworkSnapshotRow[]>({
    queryKey: ['network-history', from, to],
    queryFn: () => networkApi.history(from!, to!),
    enabled: !!from && !!to,
    staleTime: 30_000,
  });
}
