import { useEffect } from 'react';

/**
 * Keeps the screen on while `active`: a phone that dims and locks mid-transfer suspends the page, and with it the
 * upload or download. Only works while the page is visible (and over HTTPS or on localhost); it's taken again when
 * the page comes back into view.
 */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || !('wakeLock' in navigator)) return;
    let lock: WakeLockSentinel | null = null;
    let stopped = false;
    const acquire = () => {
      if (document.visibilityState !== 'visible') return;
      navigator.wakeLock.request('screen').then(
        (sentinel) => {
          if (stopped) void sentinel.release();
          else lock = sentinel;
        },
        () => {
          // Not allowed right now (battery saver, not visible...): the transfer just goes without it.
        },
      );
    };
    acquire();
    document.addEventListener('visibilitychange', acquire);
    return () => {
      stopped = true;
      document.removeEventListener('visibilitychange', acquire);
      void lock?.release();
    };
  }, [active]);
}
