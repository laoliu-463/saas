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

function resolveDevPort() {
  const rawPort = Number(process.env.VITE_DEV_PORT || 3000)
  return Number.isFinite(rawPort) && rawPort > 0 ? rawPort : 3000
}

function resolveWatchInterval() {
  const rawInterval = Number(process.env.CHOKIDAR_INTERVAL || 1000)
  return Number.isFinite(rawInterval) && rawInterval > 0 ? rawInterval : 1000
}

function resolveUsePolling() {
  const values = [
    process.env.VITE_FORCE_POLLING,
    process.env.CHOKIDAR_USEPOLLING
  ]
  return values.some((value) => String(value || '').toLowerCase() === 'true')
}

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

export default defineConfig(() => {
  const usePolling = resolveUsePolling()

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: resolveDevPort(),
      strictPort: false,
      watch: usePolling
        ? {
            usePolling,
            interval: resolveWatchInterval(),
            ignored: ['**/node_modules/**', '**/.git/**', '**/dist/**']
          }
        : undefined,
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
  }
})
