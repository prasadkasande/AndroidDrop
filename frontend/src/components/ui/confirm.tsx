import { createContext, use, useCallback, useRef, useState, type ReactNode } from 'react';
import { AlertDialog } from 'radix-ui';
import { buttonClasses } from './button';

interface ConfirmOptions {
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  destructive?: boolean;
}

type Confirm = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Confirm | null>(null);

/** Promise-based replacement for window.confirm: {@code if (await confirm({...})) ...}. */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<ConfirmOptions>({ title: '' });
  const resolver = useRef<((value: boolean) => void) | null>(null);

  const confirm = useCallback<Confirm>((next) => {
    resolver.current?.(false);
    setOptions(next);
    setOpen(true);
    return new Promise((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const settle = (value: boolean) => {
    resolver.current?.(value);
    resolver.current = null;
    setOpen(false);
  };

  return (
    <ConfirmContext value={confirm}>
      {children}
      <AlertDialog.Root open={open} onOpenChange={(next) => !next && settle(false)}>
        <AlertDialog.Portal>
          <AlertDialog.Overlay className="fixed inset-0 z-[60] bg-overlay data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
          <AlertDialog.Content className="fixed top-1/2 left-1/2 z-[60] w-[calc(100%-2rem)] max-w-[400px] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-xl border border-border bg-bg shadow-dialog outline-none data-[state=closed]:animate-pop-out data-[state=open]:animate-pop-in">
            <div className="px-5 pt-5 pb-4">
              <AlertDialog.Title className="text-[15px] font-semibold tracking-[-0.01em]">{options.title}</AlertDialog.Title>
              {options.description ? (
                <AlertDialog.Description className="mt-1.5 text-[13.5px] text-fg-muted">{options.description}</AlertDialog.Description>
              ) : (
                <AlertDialog.Description className="sr-only">{options.title}</AlertDialog.Description>
              )}
            </div>
            <div className="flex justify-end gap-2 border-t border-border bg-bg-subtle px-5 py-3">
              <AlertDialog.Cancel className={buttonClasses({ variant: 'secondary', size: 'sm' })}>Cancel</AlertDialog.Cancel>
              <AlertDialog.Action className={buttonClasses({ variant: options.destructive ? 'danger' : 'primary', size: 'sm' })} onClick={() => settle(true)}>
                {options.confirmLabel ?? 'Confirm'}
              </AlertDialog.Action>
            </div>
          </AlertDialog.Content>
        </AlertDialog.Portal>
      </AlertDialog.Root>
    </ConfirmContext>
  );
}

export function useConfirm(): Confirm {
  const confirm = use(ConfirmContext);
  if (!confirm) throw new Error('useConfirm must be used inside ConfirmProvider');
  return confirm;
}
