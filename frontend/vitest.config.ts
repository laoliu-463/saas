import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      tempDirectory: resolve(__dirname, 'node_modules/.vitest/coverage-tmp'),
      include: [
        'src/**/*.ts',
        'src/**/*.tsx',
      ],
      exclude: [
        'src/**/*.d.ts',
        'src/**/*.test.ts',
        'src/**/*.test.tsx',
        'src/**/*.spec.ts',
        'src/**/*.spec.tsx',
        'src/**/types/**',
        'src/env.d.ts',
        'src/main.ts',
        'src/router/index.ts',      // 路由表太大，暂不纳入
      ],
      thresholds: {
        statements: 75,
        branches: 70,
        functions: 65,
        lines: 75,
        // per-file 阈值（无测试或低测试密度的模块）
        'src/utils/request.ts': 34,   // 拦截器动态逻辑 Vitest 无法覆盖，30个纯函数测试
        'src/utils/shippingBatch.ts': 0,
        'src/utils/media.ts': 0,
        'src/stores/app.ts': 0,
        'src/composables/useRuntimeEnvironment.ts': 0,
        'src/theme/brand.ts': 0,
        'src/theme/naiveTheme.ts': 0,
      }
    }
  }
})
