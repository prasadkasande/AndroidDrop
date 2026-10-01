import type { ComponentProps } from 'react';
import { cn } from '../../lib/cn';

const VARIANTS = {
  primary: 'bg-accent text-accent-fg hover:bg-accent-hover',
  secondary: 'bg-bg-muted text-fg hover:bg-bg-emphasis',
  ghost: 'text-fg-muted hover:bg-bg-muted hover:text-fg',
  danger: 'bg-danger text-white hover:opacity-90 dark:text-canvas',
  'danger-ghost': 'text-fg-muted hover:bg-danger-subtle hover:text-danger',
} as const;

/** Text buttons are pills; icon-only buttons are rounded squares, like Mac toolbar buttons. On touch screens every size
 * grows to a finger-sized target (44–48px). */
const SIZES = {
  xs: 'h-7 gap-1.5 rounded-full px-3 text-[12.5px] [&_svg]:size-3.5 touch:h-10 touch:px-4 touch:text-[14.5px] touch:[&_svg]:size-4',
  sm: 'h-8 gap-1.5 rounded-full px-3.5 text-[13px] [&_svg]:size-4 touch:h-11 touch:gap-2 touch:px-5 touch:text-[15px] touch:[&_svg]:size-[18px]',
  md: 'h-9 gap-2 rounded-full px-4 text-[13.5px] [&_svg]:size-4 touch:h-12 touch:px-5 touch:text-[16px] touch:[&_svg]:size-5',
  lg: 'h-10 gap-2 rounded-full px-5 text-[14px] [&_svg]:size-4 touch:h-12 touch:px-6 touch:text-[16px] touch:[&_svg]:size-5',
  icon: 'size-9 rounded-lg [&_svg]:size-4 touch:size-11 touch:rounded-xl touch:[&_svg]:size-5',
  'icon-sm': 'size-8 rounded-lg [&_svg]:size-4 touch:size-11 touch:rounded-xl touch:[&_svg]:size-5',
  'icon-xs': 'size-7 rounded-md [&_svg]:size-3.5 touch:size-10 touch:rounded-lg touch:[&_svg]:size-[18px]',
} as const;

interface ButtonStyle {
  variant?: keyof typeof VARIANTS;
  size?: keyof typeof SIZES;
  className?: string;
}

/** Shared by <Button> and links styled as buttons. */
export function buttonClasses({ variant = 'secondary', size = 'md', className }: ButtonStyle = {}): string {
  return cn(
    'inline-flex shrink-0 select-none items-center justify-center font-medium whitespace-nowrap transition-colors duration-100',
    'outline-none focus-visible:ring-[3px] focus-visible:ring-accent/30',
    'disabled:pointer-events-none disabled:opacity-45 [&_svg]:shrink-0',
    VARIANTS[variant],
    SIZES[size],
    className,
  );
}

export function Button({ variant, size, className, type = 'button', ...props }: ComponentProps<'button'> & ButtonStyle) {
  return <button type={type} className={buttonClasses({ variant, size, className })} {...props} />;
}
