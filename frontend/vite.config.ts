import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import type { ProxyOptions } from 'vite'

function resolveProxyTarget() {
  const explicit = process.env.VITE_PROXY_TARGET
  if (explicit && explicit.trim()) {
    return explicit.trim()
  }

  return 'http://localhost:8080'
}

const proxyTarget = resolveProxyTarget()

function createApiProxy(options?: ProxyOptions): ProxyOptions {
  return {
    target: proxyTarget,
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('error', (_err, _req, res) => {
        if (!res || !('writeHead' in res) || typeof res.writeHead !== 'function') {
          return
        }
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' })
        res.end(JSON.stringify({
          code: 502,
          msg: '代理转发失败，后端服务可能正在热重启，请稍后重试',
          timestamp: Date.now()
        }))
      })
    },
    ...options
  }
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 3000,
    watch: {
      usePolling: true,
      interval: 300
    },
    proxy: {
      '/api': createApiProxy(),
      '/douyin': createApiProxy({
        rewrite: (path) => `/api${path}`
      })
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/naive-ui')) {
            return 'naive-ui'
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/@vue') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) {
            return 'vue-vendor'
          }
        }
      }
    }
  }
})
