import { resolve } from 'path';
import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        kill: resolve(__dirname, 'kill.html')
      }
    }
  },
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
