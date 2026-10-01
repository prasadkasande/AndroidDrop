import { useRef, useState, type DragEvent } from 'react';
import { draggingFiles, filesFromDrop } from './useWindowFileDrop';

/**
 * Makes an element accept dropped files. Uses an enter/leave counter so moving over child elements doesn't flicker
 * the highlight, and stops the drop from also reaching the page-wide handler.
 */
export function useDropTarget(onDrop: (files: File[], skippedFolders: number) => void) {
  const [over, setOver] = useState(false);
  const depth = useRef(0);

  const handlers = {
    onDragEnter: (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      event.preventDefault();
      depth.current += 1;
      setOver(true);
    },
    onDragOver: (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
    },
    onDragLeave: (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      depth.current = Math.max(0, depth.current - 1);
      if (depth.current === 0) setOver(false);
    },
    onDrop: (event: DragEvent) => {
      if (!draggingFiles(event)) return;
      event.preventDefault();
      event.stopPropagation();
      depth.current = 0;
      setOver(false);
      const { files, folders } = filesFromDrop(event.dataTransfer);
      onDrop(files, folders);
    },
  };

  return { over, handlers };
}
