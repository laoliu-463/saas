import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import type { ProxyOptions } from 'vite'

function resolveProxyTarget() {
  const explicit = process.env.VITE_PROXY_TARGET
  if (explicit && explicit.trim()) {
    return explicit.trim()
  }

  const apiBase = process.env.VITE_API_BASE_URL
  if (apiBase && apiBase.trim()) {
    return apiBase.trim().replace(/\/api\/?$/, '')
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
      // Docker Desktop on Windows sometimes misses fs events from bind mounts.
      // Polling makes HMR deterministic for mounted source files.
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
})
