import { useCallback, useEffect, useState } from 'react';
import { WifiOff } from 'lucide-react';
import { LogoMark } from './components/Brand';
import { Home } from './components/Home';
import { PairScreen } from './components/PairScreen';
import { Button } from './components/ui/button';
import { ConfirmProvider } from './components/ui/confirm';
import { Spinner } from './components/ui/field';
import { Toaster } from './components/ui/toaster';
import { TooltipProvider } from './components/ui/tooltip';
import { api, ApiError, type Session } from './lib/api';
import { moveToSecure, secureOrigin, takeParam } from './lib/secure';
import { ThemeProvider } from './lib/theme';

type View =
  | { kind: 'loading' }
  | { kind: 'offline'; message: string }
  | { kind: 'unpaired'; notice?: string }
  | { kind: 'ready'; session: Session };

export default function App() {
  return (
    <ThemeProvider>
      <TooltipProvider>
        <ConfirmProvider>
          <Root />
          <Toaster />
        </ConfirmProvider>
      </TooltipProvider>
    </ThemeProvider>
  );
}

function Root() {
  const [view, setView] = useState<View>({ kind: 'loading' });
  // A phone on plain HTTP that could move to HTTPS but doesn't trust the certificate yet.
  const [secureSetup, setSecureSetup] = useState<string | null>(null);

  const load = useCallback(async () => {
    setView({ kind: 'loading' });
    // Arriving from the HTTP address: this carries the phone's pairing over. The session call below says if it worked.
    const handoff = takeParam('handoff');
    if (handoff) await api.finishHandoff(handoff).catch(() => undefined);
    const [server, result] = await Promise.all([
      api.server().catch(() => null),
      api.session().then(
        (session) => ({ session }),
        (error: unknown) => ({ error }),
      ),
    ]);
    if ('error' in result && !(result.error instanceof ApiError && result.error.status === 401)) {
      setView({ kind: 'offline', message: result.error instanceof Error ? result.error.message : String(result.error) });
      return;
    }
    const session = 'session' in result ? result.session : null;
    const origin = secureOrigin(server?.httpsPort ?? null);
    if (origin && (await moveToSecure(origin, session !== null))) return;
    setSecureSetup(origin);
    if (session) {
      takeParam('code');
      setView({ kind: 'ready', session });
    } else {
      setView({ kind: 'unpaired' });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onUnpaired = useCallback((notice: string) => setView({ kind: 'unpaired', notice }), []);
  // Ignore late updates from a Home that has already been replaced (e.g. after un-pairing).
  const onSessionChange = useCallback((session: Session) => setView((view) => (view.kind === 'ready' ? { kind: 'ready', session } : view)), []);

  switch (view.kind) {
    case 'loading':
      return (
        <main className="grid min-h-dvh place-items-center bg-canvas">
          <div className="flex items-center gap-2.5 text-[13px] text-fg-muted">
            <LogoMark />
            <Spinner className="size-3.5" />
          </div>
        </main>
      );
    case 'offline':
      return (
        <main className="grid min-h-dvh place-items-center bg-canvas px-4">
          <div className="w-full max-w-[360px]">
            <span className="grid size-10 place-items-center rounded-full border border-border bg-bg text-fg-muted">
              <WifiOff className="size-[18px]" />
            </span>
            <h1 className="mt-4 text-[18px] font-semibold tracking-[-0.02em]">Can’t reach AndroidDrop</h1>
            <p className="mt-1 text-[14px] text-fg-muted">{view.message}</p>
            <Button variant="primary" className="mt-5" onClick={() => void load()}>
              Try again
            </Button>
          </div>
        </main>
      );
    case 'unpaired':
      return (
        <PairScreen
          notice={view.notice}
          onPaired={(session) => {
            takeParam('code');
            setView({ kind: 'ready', session });
          }}
        />
      );
    case 'ready':
      return (
        <Home key={view.session.device.id} session={view.session} secureSetup={secureSetup} onSessionChange={onSessionChange} onUnpaired={onUnpaired} />
      );
  }
}
