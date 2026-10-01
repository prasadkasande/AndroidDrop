import { cn } from '../lib/cn';

const INK = '#2b2622';
const CREAM = '#fbf8f3';
/** The same warm peach and blue as the device avatars. */
const PEACH = '#ffb68c';
const BLUE = '#a9ccff';

/** Where the two devices overlap: what they share. */
const SHARED = 'M26 34a8 8 0 0 1 8-8h4v4a8 8 0 0 1-8 8h-4z';

const TILE = `<rect width="64" height="64" rx="15" fill="${INK}"/>`;
const MARK = `<rect x="11" y="11" width="27" height="27" rx="8" fill="${PEACH}"/><rect x="26" y="26" width="27" height="27" rx="8" fill="${BLUE}"/><path d="${SHARED}" fill="${CREAM}"/>`;

/** The logo as an SVG data URL, for places that take an image (the middle of the pairing QR code). */
export const LOGO_URL = svgUrl(TILE + MARK);

/** The tab icon with a red dot, shown while requests are waiting for an answer. */
const LOGO_WITH_BADGE_URL = svgUrl(TILE + MARK + `<circle cx="51" cy="13" r="11" fill="#e5484d" stroke="${CREAM}" stroke-width="3"/>`);

function svgUrl(content: string): string {
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">${content}</svg>`)}`;
}

/** Points the browser tab icon at the plain logo or the one with a badge. */
export function showFaviconBadge(badge: boolean) {
  document.querySelector('link[rel="icon"][type="image/svg+xml"]')?.setAttribute('href', badge ? LOGO_WITH_BADGE_URL : '/icon.svg');
}

/** Two devices, overlapping where they share something. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" className={cn('size-6 shrink-0', className)}>
      <rect width="64" height="64" rx="15" fill={INK} />
      <rect x="11" y="11" width="27" height="27" rx="8" fill={PEACH} />
      <rect x="26" y="26" width="27" height="27" rx="8" fill={BLUE} />
      <path d={SHARED} fill={CREAM} />
      {/* On a dark background the ink tile needs an edge to read as a tile. */}
      <rect x="0.75" y="0.75" width="62.5" height="62.5" rx="14.25" fill="none" strokeWidth="1.5" className="stroke-transparent dark:stroke-white/20" />
    </svg>
  );
}

/** Logo and name. {@code large} for the Mac sidebar. */
export function Wordmark({ large, className }: { large?: boolean; className?: string }) {
  return (
    <span className={cn('flex items-center', large ? 'gap-2.5' : 'gap-2', className)}>
      <LogoMark className={large ? 'size-8' : undefined} />
      <span className={cn('font-semibold tracking-[-0.01em]', large ? 'text-[16px]' : 'text-[14px]')}>AndroidDrop</span>
    </span>
  );
}
