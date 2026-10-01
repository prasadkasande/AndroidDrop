import { useCallback, useState } from 'react';

const PREF_KEY = 'androiddrop:notifications';

function readPref(): string | null {
  try {
    return localStorage.getItem(PREF_KEY);
  } catch {
    return null;
  }
}

function writePref(value: string) {
  try {
    localStorage.setItem(PREF_KEY, value);
  } catch {
    // Private mode etc. The toggle still works for this visit.
  }
}

/**
 * System notifications for items that arrive while the tab is in the background. Browsers only allow them in a
 * secure context, which in practice means the host at http://localhost (phones reach us over plain HTTP).
 */
export function useSystemNotifications() {
  const supported = typeof Notification !== 'undefined' && window.isSecureContext;
  const [enabled, setEnabled] = useState(() => supported && Notification.permission === 'granted' && readPref() !== 'off');

  const toggle = useCallback(async () => {
    if (enabled) {
      setEnabled(false);
      writePref('off');
      return;
    }
    const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    if (permission === 'granted') {
      setEnabled(true);
      writePref('on');
    }
  }, [enabled]);

  const show = useCallback(
    (title: string, body: string) => {
      if (enabled && document.hidden) new Notification(title, { body, icon: '/icon-192.png' });
    },
    [enabled],
  );

  return { supported, enabled, toggle, show };
}
