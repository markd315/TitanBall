import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:3030',
        changeOrigin: true
      },
      '/game': {
        target: 'ws://localhost:3030',
        ws: true
      }
    }
  }
});
