import type { ReactNode } from 'react';
import type { View } from './Sidebar';

/**
 * AndroidDrop's own icons for its three sections: signal waves for Nearby, a tray taking something in for Received,
 * a paper plane for Sent. Drawn in two tones: the strokes take the text colour, the fill layer whatever
 * `fillClassName` gives it (nothing by default).
 */
const GLYPHS: Record<View, { fill: ReactNode; stroke: ReactNode }> = {
  send: {
    fill: <circle cx="12" cy="12" r="2.6" />,
    stroke: (
      <>
        <path d="M8.4 8.4a5.1 5.1 0 0 0 0 7.2M15.6 8.4a5.1 5.1 0 0 1 0 7.2M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8" />
        <circle cx="12" cy="12" r="2.6" />
      </>
    ),
  },
  received: {
    fill: <path d="M3.8 13.6h4.4l1.6 2.6h4.4l1.6-2.6h4.4v4a2.4 2.4 0 0 1-2.4 2.4H6.2a2.4 2.4 0 0 1-2.4-2.4z" />,
    stroke: (
      <>
        <path d="M3.8 13.6h4.4l1.6 2.6h4.4l1.6-2.6h4.4v4a2.4 2.4 0 0 1-2.4 2.4H6.2a2.4 2.4 0 0 1-2.4-2.4z" />
        <path d="M12 3.6v8.6M8.6 9l3.4 3.4L15.4 9" />
      </>
    ),
  },
  sent: {
    fill: <path d="M20.4 3.6 3.9 10.4l6.7 2.9 2.9 6.7z" />,
    stroke: <path d="M20.4 3.6 3.9 10.4l6.7 2.9 2.9 6.7zM20.4 3.6l-9.8 9.7" />,
  },
};

/**
 * Each section's own colours, worn while it's open, in light and dark. Full class strings so Tailwind can see them:
 * `card` the box, `selected` its outline and lift, `halo` the circle behind the icon, `ink` the icon and details,
 * `wash` the icon's fill, `title` the name, `solid` a badge on the box.
 */
export const SECTION_COLOURS: Record<View, Record<'card' | 'selected' | 'halo' | 'ink' | 'wash' | 'title' | 'solid', string>> = {
  send: {
    card: 'bg-[#f1f9f4] dark:bg-[#1a2820]',
    selected:
      'border-[#2f6b47] shadow-[0_0_0_1px_#2f6b47,0_14px_30px_-16px_#5aa878] dark:border-[#a9dcbb] dark:shadow-[0_0_0_1px_#a9dcbb,0_14px_30px_-16px_#1f3b2a]',
    halo: 'bg-[#d6efdf] dark:bg-[#243d2d]',
    ink: 'text-[#2f6b47] dark:text-[#a9dcbb]',
    wash: 'fill-[#5aa878]',
    title: 'text-[#1f4a31] dark:text-[#d5f0de]',
    solid: 'bg-[#2f6b47] text-white dark:bg-[#a9dcbb] dark:text-[#12281b]',
  },
  received: {
    card: 'bg-[#f1f6ff] dark:bg-[#1a2232]',
    selected:
      'border-[#2a4f86] shadow-[0_0_0_1px_#2a4f86,0_14px_30px_-16px_#5b8fd6] dark:border-[#b9d4ff] dark:shadow-[0_0_0_1px_#b9d4ff,0_14px_30px_-16px_#1c2c47]',
    halo: 'bg-[#dbe9fd] dark:bg-[#26385a]',
    ink: 'text-[#2a4f86] dark:text-[#b9d4ff]',
    wash: 'fill-[#5b8fd6]',
    title: 'text-[#1c3a66] dark:text-[#dbe8ff]',
    solid: 'bg-[#2a4f86] text-white dark:bg-[#b9d4ff] dark:text-[#14233a]',
  },
  sent: {
    card: 'bg-[#fff6f0] dark:bg-[#2f221a]',
    selected:
      'border-[#8a4a24] shadow-[0_0_0_1px_#8a4a24,0_14px_30px_-16px_#e0895a] dark:border-[#ffc9a8] dark:shadow-[0_0_0_1px_#ffc9a8,0_14px_30px_-16px_#3b2518]',
    halo: 'bg-[#fde0cf] dark:bg-[#4d3325]',
    ink: 'text-[#8a4a24] dark:text-[#ffc9a8]',
    wash: 'fill-[#e0895a]',
    title: 'text-[#6a3517] dark:text-[#ffe3d1]',
    solid: 'bg-[#8a4a24] text-white dark:bg-[#ffc9a8] dark:text-[#3a1f10]',
  },
};

export function SectionIcon({
  section,
  className,
  fillClassName,
  strokeWidth = 1.8,
}: {
  section: View;
  className?: string;
  fillClassName?: string;
  strokeWidth?: number;
}) {
  const glyph = GLYPHS[section];
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {fillClassName && (
        <g stroke="none" className={fillClassName}>
          {glyph.fill}
        </g>
      )}
      {glyph.stroke}
    </svg>
  );
}
