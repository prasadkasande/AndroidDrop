import { useState } from 'react';
import { Share2, X } from 'lucide-react';
import { Button } from './ui/button';

const KEY = 'androiddrop:dismissed-notice';

function dismissed(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

interface Props {
  /** Set up HTTPS first; then offer to install. */
  onSecureSetup: (() => void) | null;
  onInstall: (() => void) | null;
}

/** On a phone: the one step left before AndroidDrop shows up in Android's share sheet. Dismissable per step. */
export function PhoneNotice({ onSecureSetup, onInstall }: Props) {
  const [hidden, setHidden] = useState(dismissed);
  const step = onSecureSetup ? 'secure' : onInstall ? 'install' : null;
  if (!step || hidden === step) return null;

  const dismiss = () => {
    setHidden(step);
    try {
      localStorage.setItem(KEY, step);
    } catch {
      // Stays hidden for this visit.
    }
  };

  return (
    <div className="mx-4 mt-2 flex items-center gap-3 rounded-2xl border border-border bg-bg py-3 pr-2 pl-3.5">
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-bg-muted text-fg-muted" aria-hidden="true">
        <Share2 className="size-[18px]" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[13.5px] touch:text-[15.5px] font-semibold">Share from any app</p>
        <p className="text-[12.5px] touch:text-[14px] leading-snug text-fg-muted">
          {step === 'secure' ? 'Set up a secure connection first. It takes a minute, once.' : 'Install AndroidDrop to see it in the share menu.'}
        </p>
      </div>
      <Button variant="primary" size="sm" onClick={step === 'secure' ? onSecureSetup! : onInstall!}>
        {step === 'secure' ? 'Set up' : 'Install'}
      </Button>
      <Button variant="ghost" size="icon-xs" aria-label="Dismiss" onClick={dismiss}>
        <X />
      </Button>
    </div>
  );
}
