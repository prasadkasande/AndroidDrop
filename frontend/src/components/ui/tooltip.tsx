import type { ReactNode } from 'react';
import { Tooltip } from 'radix-ui';

export function TooltipProvider({ children }: { children: ReactNode }) {
  return (
    <Tooltip.Provider delayDuration={400} skipDelayDuration={200}>
      {children}
    </Tooltip.Provider>
  );
}

/** Hover hint for an icon-only control. The child still needs its own aria-label. */
export function Tip({ label, children, side = 'bottom' }: { label: ReactNode; children: ReactNode; side?: 'top' | 'bottom' | 'left' | 'right' }) {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger asChild>{children}</Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content side={side} sideOffset={6} className="z-[70] max-w-64 rounded-md bg-fg px-2 py-1 text-[12px] font-medium text-bg shadow-popover data-[state=closed]:animate-fade-out data-[state=delayed-open]:animate-fade-in">
          {label}
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}
