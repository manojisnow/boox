import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    // Proxy API calls to the Spring Boot backend during local dev so the
    // frontend can use same-origin relative URLs and avoid CORS entirely.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // Spring Boot's Dockerfile copies `build/` into src/main/resources/static,
    // so keep CRA's output directory name instead of Vite's default `dist`.
    outDir: 'build',
  },
  optimizeDeps: {
    // Deep ESM subpath import used by Message.jsx for the Prism theme.
    include: ['react-syntax-highlighter/dist/esm/styles/prism'],
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.js'],
    globals: true,
  },
});
