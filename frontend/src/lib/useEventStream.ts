import { useEffect, useRef, useState } from 'react';
import type { Device, PairingInfo, Transfer } from './api';

export type StreamStatus = 'connecting' | 'live' | 'down';

interface EventMap {
  transfer: Transfer;
  'transfer-removed': { id: string };
  'transfer-progress': { id: string; fileId: string; received: number };
  'transfer-delivery': { id: string; fileId: string; sent: number };
  devices: Device[];
  pairing: PairingInfo;
}

type StreamHandlers = { [K in keyof EventMap]?: (data: EventMap[K]) => void } & {
  /** The stream (re)connected. Anything could have changed meanwhile, so resync. */
  ready?: () => void;
  /** The server refused or dropped the stream for good, e.g. this device was un-paired. */
  closed?: () => void;
};

const EVENT_NAMES: (keyof EventMap)[] = ['transfer', 'transfer-removed', 'transfer-progress', 'transfer-delivery', 'devices', 'pairing'];

/**
 * The server pings every 20 seconds. A stream that stays silent this long has died without the browser noticing
 * (the computer went to sleep, the phone changed Wi-Fi), so it's replaced.
 */
const QUIET_MS = 50_000;

/** Subscribes to /api/events (Server-Sent Events) and keeps reconnecting while mounted. */
export function useEventStream(handlers: StreamHandlers): StreamStatus {
  const handlersRef = useRef(handlers);
  const [status, setStatus] = useState<StreamStatus>('connecting');

  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    let source: EventSource | undefined;
    let retryTimer: number | undefined;
    let quietTimer: number | undefined;

    const connect = () => {
      window.clearTimeout(retryTimer);
      source?.close();
      const stream = new EventSource('/api/events');
      source = stream;
      const heard = () => {
        window.clearTimeout(quietTimer);
        quietTimer = window.setTimeout(() => {
          setStatus('connecting');
          connect();
        }, QUIET_MS);
      };
      heard();
      stream.addEventListener('ping', heard);
      stream.addEventListener('ready', () => {
        heard();
        setStatus('live');
        handlersRef.current.ready?.();
      });
      for (const name of EVENT_NAMES) {
        stream.addEventListener(name, (event) => {
          heard();
          const handler = handlersRef.current[name] as ((data: unknown) => void) | undefined;
          handler?.(JSON.parse((event as MessageEvent<string>).data));
        });
      }
      stream.onerror = () => {
        if (stream.readyState === EventSource.CLOSED) {
          // The browser only gives up on its own after an HTTP error; retry ourselves after checking why.
          window.clearTimeout(quietTimer);
          setStatus('down');
          handlersRef.current.closed?.();
          retryTimer = window.setTimeout(connect, 3000);
        } else {
          setStatus('connecting');
        }
      };
    };

    connect();
    return () => {
      window.clearTimeout(retryTimer);
      window.clearTimeout(quietTimer);
      source?.close();
    };
  }, []);

  return status;
}
