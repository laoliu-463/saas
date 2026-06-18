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

function normalizeModuleId(id: string) {
  return id.replaceAll('\\', '/')
}

function chunkNaiveUiModule(id: string) {
  const normalizedId = normalizeModuleId(id)
  const marker = '/node_modules/naive-ui/es/'
  const markerIndex = normalizedId.indexOf(marker)
  if (markerIndex < 0) {
    return 'naive-ui-core'
  }

  const relativePath = normalizedId.slice(markerIndex + marker.length)
  const segment = relativePath.split('/')[0]
  if (!segment || segment.startsWith('_')) {
    return 'naive-ui-core'
  }
  if (segment === 'locales') {
    return 'naive-ui-locales'
  }
  if (segment === 'styles' || segment === 'config-provider' || segment === 'global-style') {
    return 'naive-ui-foundation'
  }
  return `naive-ui-${segment}`
}

function chunkEchartsModule(id: string) {
  const normalizedId = normalizeModuleId(id)
  const marker = '/node_modules/echarts/'
  const markerIndex = normalizedId.indexOf(marker)
  if (markerIndex < 0) {
    return 'echarts-core'
  }

  const relativePath = normalizedId.slice(markerIndex + marker.length)
  const segments = relativePath.split('/')
  if (segments[0] === 'lib' && segments[1]) {
    return `echarts-${segments[1]}`
  }
  if (segments[0]) {
    return `echarts-${segments[0]}`
  }
  return 'echarts-core'
}

export default defineConfig(() => {
  const usePolling = resolveUsePolling()

  return {
    plugins: [vue()],
    cacheDir: process.env.VITE_CACHE_DIR || undefined,
    optimizeDeps: {
      include: ['vue', 'vue-router', 'pinia', 'naive-ui', 'echarts', 'axios', 'dayjs', 'xlsx']
    },
    server: {
      host: '0.0.0.0',
      port: resolveDevPort(),
      strictPort: false,
      hmr: {
        overlay: true
      },
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
    preview: {
      host: '0.0.0.0',
      port: resolveDevPort(),
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
            const normalizedId = normalizeModuleId(id)
            if (normalizedId.includes('node_modules/naive-ui')) {
              return chunkNaiveUiModule(normalizedId)
            }
            if (normalizedId.includes('node_modules/xlsx') || normalizedId.includes('node_modules/@e965/xlsx')) {
              return 'xlsx'
            }
            if (normalizedId.includes('node_modules/echarts')) {
              return chunkEchartsModule(normalizedId)
            }
            if (normalizedId.includes('node_modules/zrender')) {
              return 'zrender'
            }
            if (normalizedId.includes('node_modules/vue') || normalizedId.includes('node_modules/@vue') || normalizedId.includes('node_modules/pinia') || normalizedId.includes('node_modules/vue-router')) {
              return 'vue-vendor'
            }
          }
        }
      }
    }
  }
})
