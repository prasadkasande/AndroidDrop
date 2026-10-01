import type { Platform } from './api';

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
}

export function formatSpeed(bytesPerSecond: number): string {
  return `${formatBytes(Math.max(0, Math.round(bytesPerSecond)))}/s`;
}

/** "8s", "2 min", "1 h 5 min" */
export function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '';
  if (seconds < 60) return `${Math.max(1, Math.round(seconds))}s`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

export function timeAgo(iso: string, now: number): string {
  const seconds = Math.max(0, (now - Date.parse(iso)) / 1000);
  if (seconds < 45) return 'Just now';
  if (seconds < 90) return '1 min ago';
  if (seconds < 45 * 60) return `${Math.round(seconds / 60)} min ago`;
  const date = new Date(iso);
  return date.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export function fullDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function startOfDay(time: number): number {
  const date = new Date(time);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

/** "Today", "Yesterday", "Monday", or "12 Sep 2026" for grouping the activity feed. */
export function dayLabel(iso: string, now: number): string {
  const days = Math.round((startOfDay(now) - startOfDay(Date.parse(iso))) / 86_400_000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  const date = new Date(iso);
  if (days < 7) return date.toLocaleDateString(undefined, { weekday: 'long' });
  const sameYear = date.getFullYear() === new Date(now).getFullYear();
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', ...(sameYear ? {} : { year: 'numeric' }) });
}

/** "~/AndroidDrop" rather than "/Users/someone/AndroidDrop". */
export function tildePath(path: string): string {
  return path.replace(/^\/(Users|home)\/[^/]+/, '~');
}

export function platformLabel(platform: Platform): string {
  return { MAC: 'Mac', ANDROID: 'Android', IOS: 'iPhone', WINDOWS: 'Windows', LINUX: 'Linux', OTHER: 'Device' }[platform];
}

/** What to call the host's file manager in button labels. */
export function fileManagerName(platform: Platform): string {
  return platform === 'MAC' ? 'Finder' : platform === 'WINDOWS' ? 'Explorer' : 'Files';
}

/** What kind of device this browser runs on, as the server would tell from its user agent. */
export function guessPlatform(): Platform {
  const ua = navigator.userAgent;
  if (/Android/.test(ua)) return 'ANDROID';
  if (/iPhone|iPad/.test(ua)) return 'IOS';
  if (/Macintosh/.test(ua)) return 'MAC';
  if (/Windows/.test(ua)) return 'WINDOWS';
  if (/Linux|CrOS/.test(ua)) return 'LINUX';
  return 'OTHER';
}

/** A sensible default name for this device when pairing. The user can change it. */
export function guessDeviceName(): string {
  const ua = navigator.userAgent;
  if (/Android/.test(ua)) return /Mobile/.test(ua) ? 'Android phone' : 'Android tablet';
  if (/iPhone/.test(ua)) return 'iPhone';
  if (/iPad/.test(ua)) return 'iPad';
  if (/Macintosh/.test(ua)) return 'Mac';
  if (/Windows/.test(ua)) return 'Windows PC';
  return 'My device';
}

/** Types the in-app viewer can show (matches the server's inline allow-list). */
export function previewKindOf(mimeType: string): 'image' | 'video' | 'audio' | null {
  if (/^image\/(png|jpeg|gif|webp|avif|bmp)$/.test(mimeType)) return 'image';
  if (mimeType.startsWith('video/')) return 'video';
  if (mimeType.startsWith('audio/')) return 'audio';
  return null;
}

/** The URL if the text is nothing but a single http(s) link. */
export function singleLink(text: string): string | null {
  const trimmed = text.trim();
  return /^https?:\/\/\S+$/i.test(trimmed) ? trimmed : null;
}

export function hostnameOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

/** Clipboard API needs a secure context; phones reach us over plain HTTP, so fall back to execCommand. */
export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.appendChild(area);
  area.select();
  const ok = document.execCommand('copy');
  area.remove();
  if (!ok) throw new Error('Copy is not supported in this browser.');
}
