import { useState } from 'react';
import { cn } from '../lib/cn';

interface Props {
  value: string;
  onChange: (value: string) => void;
  length?: number;
  invalid?: boolean;
  autoFocus?: boolean;
  id?: string;
}

/**
 * One box per digit, backed by a single real <input>, so paste, SMS/OTP autofill and the numeric keyboard all
 * work as usual.
 */
export function CodeInput({ value, onChange, length = 6, invalid, autoFocus, id }: Props) {
  const [focused, setFocused] = useState(false);
  const active = Math.min(value.length, length - 1);

  return (
    <div className="relative">
      <div className="grid grid-cols-6 gap-2" aria-hidden="true">
        {Array.from({ length }, (_, index) => {
          const digit = value[index];
          const isActive = focused && index === active;
          return (
            <div
              key={index}
              className={cn(
                'grid h-12 place-items-center rounded-lg border bg-bg font-mono text-[20px] font-medium shadow-control transition-[border-color,box-shadow] duration-100 touch:h-14 touch:rounded-xl touch:text-[24px]',
                isActive ? 'border-accent ring-[3px] ring-accent/20' : invalid ? 'border-danger/60' : 'border-border',
              )}
            >
              {digit ?? (isActive ? <span className="h-5 w-px bg-fg" /> : null)}
            </div>
          );
        })}
      </div>
      <input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value.replace(/\D/g, '').slice(0, length))}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]*"
        maxLength={length}
        autoFocus={autoFocus}
        aria-label="Pairing code"
        aria-invalid={invalid}
        className="absolute inset-0 size-full cursor-text text-base caret-transparent opacity-0"
      />
    </div>
  );
}
