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

    const connect = () => {
      source = new EventSource('/api/events');
      source.addEventListener('ready', () => {
        setStatus('live');
        handlersRef.current.ready?.();
      });
      for (const name of EVENT_NAMES) {
        source.addEventListener(name, (event) => {
          const handler = handlersRef.current[name] as ((data: unknown) => void) | undefined;
          handler?.(JSON.parse((event as MessageEvent<string>).data));
        });
      }
      source.onerror = () => {
        if (source?.readyState === EventSource.CLOSED) {
          // The browser only gives up on its own after an HTTP error; retry ourselves after checking why.
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
      source?.close();
    };
  }, []);

  return status;
}
