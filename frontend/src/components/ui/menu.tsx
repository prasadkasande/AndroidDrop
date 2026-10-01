import type { ComponentProps, ReactNode } from 'react';
import { DropdownMenu } from 'radix-ui';
import { Check } from 'lucide-react';
import { cn } from '../../lib/cn';

export const Menu = DropdownMenu.Root;
export const MenuTrigger = DropdownMenu.Trigger;
export const MenuRadioGroup = DropdownMenu.RadioGroup;

export function MenuContent({ className, align = 'end', ...props }: ComponentProps<typeof DropdownMenu.Content>) {
  return (
    <DropdownMenu.Portal>
      <DropdownMenu.Content
        align={align}
        sideOffset={6}
        collisionPadding={8}
        className={cn(
          'z-50 min-w-52 overflow-hidden rounded-xl border border-border bg-bg p-1 text-[13.5px] shadow-popover outline-none touch:min-w-60 touch:rounded-2xl touch:p-1.5 touch:text-[16px]',
          'data-[state=closed]:animate-pop-out data-[state=open]:animate-pop-in',
          className,
        )}
        {...props}
      />
    </DropdownMenu.Portal>
  );
}

const ITEM =
  'relative flex min-h-8 cursor-default select-none items-center gap-2.5 rounded-md px-2 outline-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[highlighted]:bg-bg-muted [&_svg]:size-4 [&_svg]:shrink-0 touch:min-h-12 touch:gap-3 touch:rounded-lg touch:px-3 touch:[&_svg]:size-5';

export function MenuItem({ className, destructive, ...props }: ComponentProps<typeof DropdownMenu.Item> & { destructive?: boolean }) {
  return (
    <DropdownMenu.Item
      className={cn(ITEM, destructive ? 'text-danger data-[highlighted]:bg-danger-subtle' : '[&_svg]:text-fg-muted', className)}
      {...props}
    />
  );
}

export function MenuRadioItem({ children, className, ...props }: ComponentProps<typeof DropdownMenu.RadioItem>) {
  return (
    <DropdownMenu.RadioItem className={cn(ITEM, 'pr-8 [&_svg]:text-fg-muted', className)} {...props}>
      {children}
      <DropdownMenu.ItemIndicator className="absolute right-2">
        <Check className="!text-fg" />
      </DropdownMenu.ItemIndicator>
    </DropdownMenu.RadioItem>
  );
}

/** An on/off switch's look; the control around it provides the role and label. */
export function Switch({ checked }: { checked: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn('mt-0.5 flex h-[18px] w-8 shrink-0 items-center rounded-full p-0.5 transition-colors duration-150 touch:mt-0 touch:h-7 touch:w-12', checked ? 'bg-accent' : 'bg-bg-emphasis')}
    >
      <span className={cn('size-3.5 rounded-full bg-white shadow-sm transition-transform duration-150 touch:size-6', checked && 'translate-x-3.5 touch:translate-x-5')} />
    </span>
  );
}

export function MenuLabel({ children, className }: { children: ReactNode; className?: string }) {
  return <DropdownMenu.Label className={cn('px-2 pt-2 pb-1 text-[12px] font-medium text-fg-subtle touch:px-3 touch:text-[13.5px]', className)}>{children}</DropdownMenu.Label>;
}

export function MenuSeparator() {
  return <DropdownMenu.Separator className="-mx-1 my-1 h-px bg-border" />;
}
