import type { MouseEvent, ReactNode } from 'react';

export type ToastTone = 'default' | 'success' | 'error' | 'loading';

export interface ToastOptions {
  /** Showing a toast with the id of one on screen replaces it in place (say "Saving…" becoming "Saved"). */
  id?: string;
  description?: ReactNode;
  /** Milliseconds on screen; Infinity keeps it until it's replaced or closed. */
  duration?: number;
  /** A button on the toast. It closes the toast unless the click handler calls `preventDefault()`. */
  action?: { label: string; onClick: (event: MouseEvent<HTMLButtonElement>) => void };
}

export interface ToastItem extends ToastOptions {
  id: string;
  title: string;
  tone: ToastTone;
  /** Playing its exit animation. */
  leaving?: boolean;
}

const VISIBLE = 3;
const EXIT_MS = 150;
const DURATION: Record<ToastTone, number> = { default: 4000, success: 4000, error: 6000, loading: Infinity };

let toasts: ToastItem[] = [];
let nextId = 0;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

function show(tone: ToastTone, title: string, options: ToastOptions = {}): string {
  const id = options.id ?? `toast-${++nextId}`;
  const item: ToastItem = { ...options, id, title, tone, duration: options.duration ?? DURATION[tone] };
  toasts = toasts.some((existing) => existing.id === id)
    ? toasts.map((existing) => (existing.id === id ? item : existing))
    : [...toasts, item].slice(-VISIBLE);
  emit();
  return id;
}

function dismiss(id: string) {
  if (!toasts.some((item) => item.id === id && !item.leaving)) return;
  toasts = toasts.map((item) => (item.id === id ? { ...item, leaving: true } : item));
  emit();
  window.setTimeout(() => {
    // Replaced by a new toast with this id meanwhile: that one stays.
    toasts = toasts.filter((item) => item.id !== id || !item.leaving);
    emit();
  }, EXIT_MS);
}

/** Brief messages in the corner (above the tab bar on a phone). */
export const toast = Object.assign((title: string, options?: ToastOptions) => show('default', title, options), {
  success: (title: string, options?: ToastOptions) => show('success', title, options),
  error: (title: string, options?: ToastOptions) => show('error', title, options),
  loading: (title: string, options?: ToastOptions) => show('loading', title, options),
  dismiss,
});

export const toastStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  snapshot: () => toasts,
};
