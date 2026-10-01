import { useEffect, useState } from 'react';
import { WifiOff } from 'lucide-react';
import type { StreamStatus } from '../lib/useEventStream';
import { Button } from './ui/button';
import { Spinner } from './ui/field';

/** Explains a lost connection, but only if it lasts long enough to matter (brief blips are normal). */
export function ConnectionBanner({ status, serverName, isHost }: { status: StreamStatus; serverName: string; isHost: boolean }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (status === 'live') {
      setVisible(false);
      return;
    }
    const timer = window.setTimeout(() => setVisible(true), 1500);
    return () => window.clearTimeout(timer);
  }, [status]);

  if (!visible) return null;

  const message =
    status === 'connecting'
      ? `Reconnecting to ${isHost ? 'the AndroidDrop server' : serverName}…`
      : isHost
        ? 'The AndroidDrop server isn’t running. Start it again to keep sharing.'
        : `Can’t reach ${serverName}. Make sure both devices are on the same Wi-Fi.`;

  return (
    <div role="status" className="flex items-center gap-2.5 border-b border-warning/25 bg-warning-subtle px-4 py-2 text-[13px] touch:text-[15px] sm:px-8">
      {status === 'connecting' ? <Spinner className="size-3.5 text-warning" /> : <WifiOff className="size-4 text-warning" />}
      <span className="min-w-0 flex-1">{message}</span>
      {status === 'down' && (
        <Button variant="ghost" size="xs" onClick={() => window.location.reload()}>
          Retry
        </Button>
      )}
    </div>
  );
}
