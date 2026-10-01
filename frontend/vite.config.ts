import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

declare const process: { env: Record<string, string | undefined> };

/** Where the dev server forwards /api. Override to preview against a second backend. */
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // xfwd lets the server see the real client address, so a phone using the dev server still has to pair.
      '/api': { target: apiTarget, xfwd: true },
    },
  },
  build: {
    // Bundled into the Spring Boot jar and served from the same origin as the API.
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
});
