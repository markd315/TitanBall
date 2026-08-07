import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/pages/titanball/api': {
        target: 'http://127.0.0.1:3030',
        changeOrigin: true
      },
      '/pages/titanball/game': {
        target: 'http://127.0.0.1:3030',
        ws: true,
        changeOrigin: true
      }
    }
  }
});
