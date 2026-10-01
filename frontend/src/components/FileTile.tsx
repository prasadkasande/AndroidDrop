import { useState } from 'react';
import { cn } from '../lib/cn';
import { KIND_STYLES, kindOf, type FileKind } from '../lib/fileKinds';

const SIZES = {
  sm: { box: 'size-8 rounded-md', icon: 'size-4' },
  md: { box: 'size-10 rounded-lg', icon: 'size-[18px]' },
  lg: { box: 'size-16 rounded-lg', icon: 'size-6' },
};

interface Props {
  name: string;
  mimeType: string;
  /** Overrides the kind derived from name/type (notes and links). */
  kind?: FileKind;
  imageUrl?: string | null;
  size?: keyof typeof SIZES;
  muted?: boolean;
  className?: string;
}

/** Thumbnail when there is one, otherwise a neutral tile with a type-tinted icon. */
export function FileTile({ name, mimeType, kind, imageUrl, size = 'md', muted, className }: Props) {
  const [broken, setBroken] = useState(false);
  const style = KIND_STYLES[kind ?? kindOf(name, mimeType)];
  const Icon = style.icon;
  const s = SIZES[size];
  const showImage = imageUrl && !broken;

  return (
    <span
      aria-hidden="true"
      className={cn('grid shrink-0 place-items-center overflow-hidden border border-border bg-bg-subtle', s.box, muted && 'opacity-50', className)}
    >
      {showImage ? (
        <img src={imageUrl} alt="" loading="lazy" decoding="async" className="size-full object-cover" onError={() => setBroken(true)} />
      ) : (
        <Icon className={cn(s.icon, style.tone)} strokeWidth={1.75} />
      )}
    </span>
  );
}
