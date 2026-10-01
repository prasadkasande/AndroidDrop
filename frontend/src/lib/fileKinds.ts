import {
  AppWindow,
  FileArchive,
  FileAudio,
  FileCode,
  FileSpreadsheet,
  FileText,
  FileVideo,
  File as FileIcon,
  Image,
  Link,
  MessageSquareText,
  Presentation,
  Smartphone,
  type LucideIcon,
} from 'lucide-react';

export type FileKind =
  | 'image'
  | 'video'
  | 'audio'
  | 'pdf'
  | 'archive'
  | 'document'
  | 'spreadsheet'
  | 'presentation'
  | 'apk'
  | 'app'
  | 'code'
  | 'text'
  | 'link'
  | 'other';

interface KindStyle {
  icon: LucideIcon;
  /** Icon colour. Full class strings so Tailwind can see them. */
  tone: string;
}

export const KIND_STYLES: Record<FileKind, KindStyle> = {
  image: { icon: Image, tone: 'text-violet-500' },
  video: { icon: FileVideo, tone: 'text-pink-500' },
  audio: { icon: FileAudio, tone: 'text-rose-500' },
  pdf: { icon: FileText, tone: 'text-red-500' },
  archive: { icon: FileArchive, tone: 'text-amber-600 dark:text-amber-500' },
  document: { icon: FileText, tone: 'text-blue-500' },
  spreadsheet: { icon: FileSpreadsheet, tone: 'text-green-600 dark:text-green-500' },
  presentation: { icon: Presentation, tone: 'text-orange-500' },
  apk: { icon: Smartphone, tone: 'text-emerald-600 dark:text-emerald-500' },
  app: { icon: AppWindow, tone: 'text-fg-muted' },
  code: { icon: FileCode, tone: 'text-fg-muted' },
  text: { icon: MessageSquareText, tone: 'text-fg-muted' },
  link: { icon: Link, tone: 'text-accent' },
  other: { icon: FileIcon, tone: 'text-fg-subtle' },
};

const BY_EXTENSION: Record<string, FileKind> = {
  apk: 'apk', aab: 'apk', dmg: 'app', pkg: 'app', exe: 'app', msi: 'app',
  zip: 'archive', rar: 'archive', '7z': 'archive', tar: 'archive', gz: 'archive', tgz: 'archive', bz2: 'archive', xz: 'archive',
  pdf: 'pdf',
  doc: 'document', docx: 'document', pages: 'document', rtf: 'document', odt: 'document', md: 'document', txt: 'document',
  xls: 'spreadsheet', xlsx: 'spreadsheet', numbers: 'spreadsheet', csv: 'spreadsheet', ods: 'spreadsheet',
  ppt: 'presentation', pptx: 'presentation', key: 'presentation', odp: 'presentation',
  js: 'code', ts: 'code', tsx: 'code', java: 'code', kt: 'code', py: 'code', json: 'code', xml: 'code', html: 'code', css: 'code', sh: 'code', swift: 'code', go: 'code', rs: 'code',
  heic: 'image', heif: 'image', svg: 'image',
};

function extensionOf(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
}

export function kindOf(name: string, mimeType: string): FileKind {
  const byExtension = BY_EXTENSION[extensionOf(name)];
  if (byExtension) return byExtension;
  if (mimeType.startsWith('image/')) return 'image';
  if (mimeType.startsWith('video/')) return 'video';
  if (mimeType.startsWith('audio/')) return 'audio';
  if (mimeType === 'application/pdf') return 'pdf';
  if (/zip|compressed|x-tar|x-rar|x-7z|gzip/.test(mimeType)) return 'archive';
  if (mimeType.startsWith('text/')) return 'document';
  return 'other';
}
