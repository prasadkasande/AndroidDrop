import { useEffect, useRef, useState, type DragEvent as ReactDragEvent } from 'react';

export function draggingFiles(event: DragEvent | ReactDragEvent): boolean {
  return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}

/** Files from a drop, skipping folders (a browser can't stream a directory as one upload). */
export function filesFromDrop(dataTransfer: DataTransfer | null): { files: File[]; folders: number } {
  const entries = Array.from(dataTransfer?.items ?? []).filter((item) => item.kind === 'file');
  const isFolder = (item: DataTransferItem) => item.webkitGetAsEntry()?.isDirectory === true;
  return {
    folders: entries.filter(isFolder).length,
    files: entries
      .filter((item) => !isFolder(item))
      .map((item) => item.getAsFile())
      .filter((file): file is File => file !== null),
  };
}

/**
 * Accepts files dropped anywhere on the page or pasted with Cmd/Ctrl+V, and reports whether a file drag is in
 * progress. Drop targets that handle a drop themselves (device tiles) stop propagation; the capture listener still
 * resets the dragging state.
 */
export function useWindowFileDrop(onFiles: (files: File[], skippedFolders: number) => void): boolean {
  const [dragging, setDragging] = useState(false);
  const onFilesRef = useRef(onFiles);

  useEffect(() => {
    onFilesRef.current = onFiles;
  });

  useEffect(() => {
    let depth = 0;
    const reset = () => {
      depth = 0;
      setDragging(false);
    };
    const enter = (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      event.preventDefault();
      depth += 1;
      setDragging(true);
    };
    const over = (event: DragEvent) => {
      if (draggingFiles(event)) event.preventDefault();
    };
    const leave = (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      depth = Math.max(0, depth - 1);
      if (depth === 0) setDragging(false);
    };
    const drop = (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      event.preventDefault();
      const { files, folders } = filesFromDrop(event.dataTransfer);
      if (files.length || folders) onFilesRef.current(files, folders);
    };
    const paste = (event: ClipboardEvent) => {
      const files = Array.from(event.clipboardData?.files ?? []);
      const target = event.target as HTMLElement | null;
      if (!files.length || target?.closest('input, textarea')) return;
      event.preventDefault();
      onFilesRef.current(files, 0);
    };

    window.addEventListener('dragenter', enter);
    window.addEventListener('dragover', over);
    window.addEventListener('dragleave', leave);
    window.addEventListener('drop', reset, true);
    window.addEventListener('drop', drop);
    window.addEventListener('paste', paste);
    return () => {
      window.removeEventListener('dragenter', enter);
      window.removeEventListener('dragover', over);
      window.removeEventListener('dragleave', leave);
      window.removeEventListener('drop', reset, true);
      window.removeEventListener('drop', drop);
      window.removeEventListener('paste', paste);
    };
  }, []);

  return dragging;
}
