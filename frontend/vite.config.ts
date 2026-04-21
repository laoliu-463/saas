import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    watch: {
      // Docker Desktop on Windows sometimes misses fs events from bind mounts.
      // Polling makes HMR deterministic for mounted source files.
      usePolling: true,
      interval: 300
    },
    proxy: {
      '/api': {
        target: 'http://backend:8080',
        changeOrigin: true
      }
    }
  },
})
