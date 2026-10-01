import { useEffect, useState } from 'react';

interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * Chrome's "Install app" offer, which it only makes over HTTPS. Installing is what puts AndroidDrop in Android's
 * share sheet. Null when there's nothing to offer (not supported, already installed, or running as the app).
 */
export function useInstallPrompt(): (() => Promise<void>) | null {
  const [event, setEvent] = useState<InstallPromptEvent | null>(null);

  useEffect(() => {
    const offer = (next: Event) => {
      next.preventDefault(); // shown as our own button instead of Chrome's mini-bar
      setEvent(next as InstallPromptEvent);
    };
    const installed = () => setEvent(null);
    window.addEventListener('beforeinstallprompt', offer);
    window.addEventListener('appinstalled', installed);
    return () => {
      window.removeEventListener('beforeinstallprompt', offer);
      window.removeEventListener('appinstalled', installed);
    };
  }, []);

  if (!event) return null;
  return async () => {
    await event.prompt();
    if ((await event.userChoice).outcome === 'accepted') setEvent(null);
  };
}
