import { useState, type FormEvent } from 'react';
import { Button } from './ui/button';
import { Input, Spinner } from './ui/field';
import { Modal } from './ui/modal';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentName: string;
  onRename: (name: string) => Promise<boolean>;
}

export function RenameDialog({ open, onOpenChange, currentName, onRename }: Props) {
  const [name, setName] = useState(currentName);
  const [saving, setSaving] = useState(false);
  const [wasOpen, setWasOpen] = useState(open);

  // Start from the current name each time the dialog opens.
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) setName(currentName);
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim()) return;
    setSaving(true);
    if (await onRename(name.trim())) onOpenChange(false);
    setSaving(false);
  };

  const unchanged = name.trim() === currentName || !name.trim();

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="Rename this device"
      description="This is the name other devices see."
      className="sm:max-w-md"
      footer={
        <>
          <Button size="sm" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button type="submit" form="rename-form" variant="primary" size="sm" disabled={unchanged || saving}>
            {saving && <Spinner className="size-3.5" />} Save
          </Button>
        </>
      }
    >
      <form id="rename-form" onSubmit={submit}>
        <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={40} autoFocus aria-label="Device name" />
      </form>
    </Modal>
  );
}
