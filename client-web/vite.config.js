import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:3030',
        changeOrigin: true
      },
      '/game': {
        target: 'http://127.0.0.1:3030',
        ws: true,
        changeOrigin: true
      }
    }
  }
});
