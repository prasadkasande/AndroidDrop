import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { api, type Platform, type Session } from '../lib/api';
import { cn } from '../lib/cn';
import { guessDeviceName, guessPlatform, platformLabel } from '../lib/format';
import { LogoMark } from './Brand';
import { CodeInput } from './CodeInput';
import { DeviceAvatar } from './DeviceAvatar';
import { Callout } from './ui/badge';
import { Button } from './ui/button';
import { Input, Label, Spinner } from './ui/field';

const NAME_KEY = 'androiddrop:device-name';

interface PairOption {
  id: string;
  name: string;
  platform: Platform;
}

function rememberedName(): string {
  try {
    return localStorage.getItem(NAME_KEY) || guessDeviceName();
  } catch {
    return guessDeviceName();
  }
}

/** Shown to a device that hasn't paired yet. The QR code on the host opens this with ?code= filled in. */
export function PairScreen({ notice, onPaired }: { notice?: string; onPaired: (session: Session) => void }) {
  const [code, setCode] = useState(() => (new URLSearchParams(location.search).get('code') ?? '').replace(/\D/g, '').slice(0, 6));
  const [prefilled] = useState(() => code.length === 6);
  const [name, setName] = useState(rememberedName);
  const [serverName, setServerName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Devices already paired: this may be another browser on one of them, which shouldn't become a new device.
  const [existing, setExisting] = useState<PairOption[]>([]);
  const [joinId, setJoinId] = useState<string | null>(null);

  useEffect(() => {
    if (code.length !== 6) return;
    let current = true;
    api.pairOptions(code).then(
      (options) => {
        if (!current) return;
        setError(null);
        setExisting(options);
        const sameKind = options.filter((option) => option.platform === guessPlatform());
        setJoinId(sameKind.length === 1 ? sameKind[0].id : null);
      },
      (err: unknown) => current && setError(err instanceof Error ? err.message : String(err)),
    );
    return () => {
      current = false;
    };
  }, [code]);

  useEffect(() => {
    api.server().then(
      (server) => setServerName(server.name),
      () => setServerName(null),
    );
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = await api.pair(code, name.trim(), joinId ?? undefined);
      try {
        localStorage.setItem(NAME_KEY, name.trim());
      } catch {
        // Not worth failing pairing over.
      }
      onPaired(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setCode('');
      setBusy(false);
    }
  };

  const host = serverName ?? 'your computer';

  return (
    <main className="flex min-h-dvh items-center justify-center bg-canvas px-4 py-10">
      <div className="w-full max-w-[380px]">
        <LogoMark className="size-9" />
        <h1 className="mt-5 text-[24px] leading-8 font-bold tracking-[-0.022em]">Connect to {host}</h1>
        <p className="mt-1.5 text-[14px] touch:text-[16px] text-fg-muted">
          {prefilled ? 'The code was filled in from the QR code. Check the device name, then connect.' : `Enter the 6-digit code shown in AndroidDrop on ${host}.`}
        </p>

        {notice && (
          <Callout tone="warning" className="mt-5">
            {notice}
          </Callout>
        )}

        <form onSubmit={submit} className="mt-6 space-y-5 rounded-2xl border border-border bg-bg p-5">
          <div className="space-y-2">
            <Label htmlFor="pair-code">Pairing code</Label>
            <CodeInput id="pair-code" value={code} onChange={setCode} invalid={!!error && !code} autoFocus={!prefilled} />
          </div>

          {existing.length > 0 && (
            <fieldset className="space-y-2">
              <legend className="mb-2 text-[13px] touch:text-[15px] font-medium">Is this one of your devices?</legend>
              {existing.map((device) => (
                <Choice key={device.id} checked={joinId === device.id} onSelect={() => setJoinId(device.id)}>
                  <DeviceAvatar device={device} size="sm" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[13.5px] touch:text-[15.5px] font-medium">{device.name}</span>
                    <span className="block text-[12px] touch:text-[13.5px] text-fg-muted">Already paired · {platformLabel(device.platform)}</span>
                  </span>
                </Choice>
              ))}
              <Choice checked={joinId === null} onSelect={() => setJoinId(null)}>
                <span className="text-[13.5px] touch:text-[15.5px] font-medium">No, it’s a new device</span>
              </Choice>
            </fieldset>
          )}

          {joinId === null && (
            <div className="space-y-2">
              <Label htmlFor="device-name">Device name</Label>
              <Input id="device-name" value={name} onChange={(event) => setName(event.target.value)} maxLength={40} required autoComplete="off" className="h-10" />
              <p className="text-[12.5px] touch:text-[14px] text-fg-subtle">How this device appears on {host}.</p>
            </div>
          )}

          {error && <Callout tone="danger">{error}</Callout>}

          <Button type="submit" variant="primary" size="lg" className="w-full" disabled={busy || code.length !== 6 || (joinId === null && !name.trim())}>
            {busy && <Spinner className="size-3.5" />}
            Connect
          </Button>
        </form>

        <p className="mt-5 text-center text-[12.5px] touch:text-[14px] text-fg-subtle">Both devices need to be on the same Wi-Fi network.</p>
      </div>
    </main>
  );
}

function Choice({ checked, onSelect, children }: { checked: boolean; onSelect: () => void; children: ReactNode }) {
  return (
    <label
      className={cn(
        'flex cursor-pointer items-center gap-3 rounded-xl border px-3 py-2.5 transition-colors duration-100 touch:min-h-14 touch:px-4',
        checked ? 'border-accent bg-bg-subtle' : 'border-border hover:bg-bg-subtle',
      )}
    >
      <input type="radio" name="pair-as" checked={checked} onChange={onSelect} className="size-4 accent-[var(--accent)] touch:size-5" />
      {children}
    </label>
  );
}
