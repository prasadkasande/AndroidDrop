import type { ReactNode } from 'react';
import { Dialog } from 'radix-ui';
import { X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Button } from './button';

interface ModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
}

/** A centred dialog on larger screens and a bottom sheet on phones. Focus trap and Escape come from Radix. */
export function Modal({ open, onOpenChange, title, description, children, footer, className }: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-overlay data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
        <Dialog.Content
          className={cn(
            'fixed z-50 flex flex-col overflow-hidden border-border bg-bg shadow-dialog outline-none',
            // Phone: bottom sheet.
            'inset-x-0 bottom-0 max-h-[92dvh] rounded-t-2xl border-t pb-[env(safe-area-inset-bottom)] data-[state=closed]:animate-sheet-out data-[state=open]:animate-sheet-in',
            // Larger screens: centred.
            'sm:inset-x-auto sm:bottom-auto sm:top-1/2 sm:left-1/2 sm:max-h-[calc(100dvh-4rem)] sm:w-[calc(100%-2rem)] sm:max-w-lg sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:border sm:pb-0',
            'sm:data-[state=closed]:animate-pop-out sm:data-[state=open]:animate-pop-in',
            className,
          )}
          {...(description ? {} : { 'aria-describedby': undefined })}
        >
          <div className="flex items-start justify-between gap-4 px-5 pt-5 touch:pt-6">
            <div className="min-w-0 space-y-1">
              <Dialog.Title className="text-[15px] font-semibold tracking-[-0.01em] touch:text-[19px]">{title}</Dialog.Title>
              {description && <Dialog.Description className="text-[13px] text-fg-muted touch:text-[15px]">{description}</Dialog.Description>}
            </div>
            <Dialog.Close asChild>
              <Button variant="ghost" size="icon-xs" aria-label="Close" className="-mt-0.5 -mr-1.5 touch:-mt-2 touch:-mr-2">
                <X />
              </Button>
            </Dialog.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-5 pt-4 pb-5">{children}</div>
          {footer && <div className="flex flex-col-reverse gap-2 border-t border-border bg-bg-subtle px-5 py-3 touch:gap-2.5 touch:py-4 sm:flex-row sm:justify-end">{footer}</div>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
