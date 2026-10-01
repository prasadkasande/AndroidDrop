import { useState } from 'react';
import { ChevronDown, Folder, FolderSearch } from 'lucide-react';
import type { SaveFolder } from '../lib/api';
import { tildePath } from '../lib/format';
import { Spinner } from './ui/field';
import { Menu, MenuContent, MenuItem, MenuLabel, MenuRadioGroup, MenuRadioItem, MenuSeparator, MenuTrigger } from './ui/menu';

/** Where the host can save files it accepts. */
export interface FolderChoices {
  /** The storage folder first, then recently used ones. */
  folders: SaveFolder[];
  /** Whether "Choose folder…" can open the computer's own folder picker. */
  canChoose: boolean;
  /** Opens that picker; null if it was cancelled. */
  choose: () => Promise<SaveFolder | null>;
}

interface Props {
  choices: FolderChoices;
  value: SaveFolder;
  onChange: (folder: SaveFolder) => void;
  align?: 'start' | 'center' | 'end';
}

/** A compact "AndroidDrop ⌄" button that picks the folder accepted files go into. */
export function FolderPicker({ choices, value, onChange, align = 'center' }: Props) {
  const [choosing, setChoosing] = useState(false);
  // A folder just picked with "Choose folder…" isn't in the recent list until files have been saved there.
  const options = choices.folders.some((folder) => folder.path === value.path) ? choices.folders : [...choices.folders, value];

  const choose = async () => {
    setChoosing(true);
    try {
      const folder = await choices.choose();
      if (folder) onChange(folder);
    } finally {
      setChoosing(false);
    }
  };

  return (
    <Menu>
      <MenuTrigger asChild>
        <button
          type="button"
          aria-label={`Save to ${value.name}. Change folder`}
          className="inline-flex max-w-52 min-w-0 items-center gap-1.5 rounded-full border border-border bg-bg py-1 pr-2 pl-2.5 text-[12.5px] font-medium text-fg outline-none hover:bg-bg-muted focus-visible:ring-[3px] focus-visible:ring-accent/25 data-[state=open]:bg-bg-muted"
        >
          {choosing ? <Spinner className="size-3.5" /> : <Folder className="size-3.5 shrink-0 text-fg-muted" />}
          <span className="truncate">{value.name}</span>
          <ChevronDown className="size-3.5 shrink-0 text-fg-subtle" />
        </button>
      </MenuTrigger>
      <MenuContent align={align} className="max-w-80">
        <MenuLabel>Save to</MenuLabel>
        <MenuRadioGroup value={value.path} onValueChange={(path) => onChange(options.find((folder) => folder.path === path) ?? value)}>
          {options.map((folder) => (
            <MenuRadioItem key={folder.path} value={folder.path} className="py-1.5">
              <Folder />
              <span className="min-w-0">
                <span className="block truncate">{folder.name}</span>
                <span className="block truncate text-[11.5px] text-fg-subtle">{tildePath(folder.path)}</span>
              </span>
            </MenuRadioItem>
          ))}
        </MenuRadioGroup>
        {choices.canChoose && (
          <>
            <MenuSeparator />
            <MenuItem onSelect={() => void choose()}>
              <FolderSearch /> Choose folder…
            </MenuItem>
          </>
        )}
      </MenuContent>
    </Menu>
  );
}
