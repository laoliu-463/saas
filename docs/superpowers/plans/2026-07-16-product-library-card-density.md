# Product Library Card Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收紧商品库卡片间距，完整展示商品名称与商品 ID，并让卡片快捷区只保留复制 ID。

**Architecture:** 保留现有 `ProductSelectionCard` 与固定行虚拟网格，不改接口和商品域规则。组件负责完整身份信息与快捷按钮，`product-library-layout.ts` 继续作为卡片高度和网格间距唯一常量源，`ProductLibrary.vue` 删除失效的单卡刷新接线。

**Tech Stack:** Vue 3、TypeScript、Vitest、Vue Test Utils、Vite、Naive UI

---

### Task 1: 用失败测试锁定完整身份信息和唯一快捷按钮

**Files:**
- Modify: `frontend/src/components/product/ProductSelectionCard.test.ts:253-294`
- Create: `frontend/src/views/product/product-library-layout.test.ts`

- [ ] **Step 1: 将旧快捷按钮测试改为新行为测试**

```ts
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

it('完整展示商品名称和商品ID，快捷按钮只保留复制ID', () => {
  const productName = '很长的商品名称用于验证卡片直接完整换行展示且不会使用两行截断'
  const productId = '1234567890123456789'
  const wrapper = mountCard({ card: { ...baseCard, productName, productId } })

  expect(wrapper.get('.selection-card__title').text()).toContain(productName)
  expect(wrapper.get('[data-testid="product-id-value"]').text()).toBe(productId)
  expect(wrapper.get('[data-testid="product-copy-id"]').exists()).toBe(true)
  expect(wrapper.find('[data-testid="product-copy-url"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="product-refresh"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="product-detail-btn"]').exists()).toBe(false)
  const source = readFileSync(resolve(process.cwd(), 'src/components/product/ProductSelectionCard.vue'), 'utf8')
  expect(source).not.toContain('-webkit-line-clamp')
})

it('复制ID写入完整商品ID', async () => {
  const productId = '1234567890123456789'
  const writeText = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
  const wrapper = mountCard({ card: { ...baseCard, productId } })

  await wrapper.get('[data-testid="product-copy-id"]').trigger('click')

  expect(writeText).toHaveBeenCalledWith(productId)
})
```

- [ ] **Step 2: 增加布局常量测试**

```ts
import { describe, expect, it } from 'vitest'
import {
  PRODUCT_LIBRARY_CARD_HEIGHT,
  PRODUCT_LIBRARY_GRID_GAP,
  PRODUCT_LIBRARY_ROW_HEIGHT
} from './product-library-layout'

describe('product-library-layout', () => {
  it('uses compact spacing and a shared row height', () => {
    expect(PRODUCT_LIBRARY_GRID_GAP).toBe(8)
    expect(PRODUCT_LIBRARY_CARD_HEIGHT).toBe(492)
    expect(PRODUCT_LIBRARY_ROW_HEIGHT).toBe(500)
  })
})
```

- [ ] **Step 3: 运行测试并确认 RED**

Run: `npm --prefix frontend test -- ProductSelectionCard.test.ts product-library-layout.test.ts`

Expected: FAIL；旧组件仍渲染复制链接、刷新、详情，且布局常量仍为 `432/16/448`。

### Task 2: 最小实现完整名称、ID 和紧凑布局

**Files:**
- Modify: `frontend/src/components/product/ProductSelectionCard.vue:105-157,294-299,559-915`
- Modify: `frontend/src/views/product/product-library-layout.ts:1-4`

- [ ] **Step 1: 重构身份信息区**

```vue
<div class="selection-card__title-row">
  <h3 class="selection-card__title" :title="card.productName">
    <svg class="selection-card__title-dy" viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
      <path d="M12.525.02c1.31-.02 2.61-.01 3.91-.01.08 1.53.63 3.02 1.59 4.23.86 1.08 2.07 1.85 3.4 2.17.02 1.34.01 2.68.01 4.02-1.58-.05-3.13-.59-4.4-1.55-.42-.32-.81-.69-1.15-1.1v7.02c0 3.42-1.93 6.16-5.18 6.94-1.92.46-4.04.14-5.73-.89-1.91-1.17-3.07-3.34-3.01-5.6.08-2.91 2.11-5.46 5-6.07.45-.1 1.08-.12 1.54-.03v4.18c-.8-.21-1.74-.08-2.39.46-.72.6-1.02 1.63-.78 2.53.25.96 1.13 1.65 2.12 1.69 1.58.07 2.45-1.09 2.46-2.52.01-3.69-.01-7.38.01-11.07.01-1.39.01-2.77.01-4.16z" />
    </svg>
    {{ card.productName }}
  </h3>
</div>
<div class="selection-card__product-id-row" @click.stop>
  <span class="selection-card__product-id-label">商品 ID</span>
  <code class="selection-card__product-id-value" data-testid="product-id-value">{{ card.productId }}</code>
  <button
    type="button"
    class="selection-card__icon-btn"
    title="复制ID"
    aria-label="复制商品ID"
    data-testid="product-copy-id"
    @click="copyField(card.productId, '商品ID')"
  >
    复制 ID
  </button>
</div>
```

- [ ] **Step 2: 删除失效按钮和 refresh 事件**

删除 `product-copy-url`、`product-refresh`、`product-detail-btn` 三个按钮，并从 `defineEmits` 删除：

```ts
refresh: [raw: Record<string, unknown>]
```

- [ ] **Step 3: 取消名称截断并允许 ID 完整换行**

```css
.selection-card__title {
  display: block;
  overflow: visible;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.selection-card__product-id-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 6px;
  margin-bottom: 8px;
}

.selection-card__product-id-value {
  min-width: 0;
  color: #475569;
  font-size: 10px;
  line-height: 18px;
  white-space: normal;
  word-break: break-all;
}
```

删除 `-webkit-line-clamp`、`-webkit-box-orient` 和标题上的 `overflow: hidden`。

- [ ] **Step 4: 更新共享布局常量**

```ts
export const PRODUCT_LIBRARY_CARD_HEIGHT = 492
export const PRODUCT_LIBRARY_GRID_GAP = 8
export const PRODUCT_LIBRARY_ROW_HEIGHT = PRODUCT_LIBRARY_CARD_HEIGHT + PRODUCT_LIBRARY_GRID_GAP
```

- [ ] **Step 5: 运行目标测试并确认 GREEN**

Run: `npm --prefix frontend test -- ProductSelectionCard.test.ts product-library-layout.test.ts`

Expected: PASS，0 failures。

### Task 3: 清理商品库页面失效刷新接线

**Files:**
- Modify: `frontend/src/views/product/ProductLibrary.vue:81-106,807-830`
- Modify: `frontend/src/views/product/ProductLibrary.test.ts`

- [ ] **Step 1: 增加页面源码契约测试并确认 RED**

```ts
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

it('does not wire removed per-card refresh actions', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/views/product/ProductLibrary.vue'), 'utf8')
  expect(source).not.toContain('@refresh="refreshProductRow"')
  expect(source).not.toContain('const refreshProductRow = async')
  expect(source).toMatch(/\.product-grid\s*\{[^}]*gap:\s*8px/s)
  expect(source).toMatch(/\.product-grid__virtual-window\s*\{[^}]*gap:\s*8px/s)
})
```

Run: `npm --prefix frontend test -- ProductLibrary.test.ts`

Expected: FAIL，源码仍包含两处监听和处理函数。

- [ ] **Step 2: 删除事件接线与死代码**

从虚拟网格和普通网格的 `ProductSelectionCard` 删除：

```vue
@refresh="refreshProductRow"
```

删除整个 `refreshProductRow` 函数；保留顶部 `refreshProducts` 和 `replaceProductRow` 的其他调用。

- [ ] **Step 3: 运行页面测试并确认 GREEN**

Run: `npm --prefix frontend test -- ProductLibrary.test.ts`

Expected: PASS，0 failures。

### Task 4: 全量验证与 Harness 交付

**Files:**
- Modify: `harness/reports/current/latest-product-library-card-density.md`（脚本生成）

- [ ] **Step 1: 运行前端测试、类型检查和构建**

Run: `npm --prefix frontend test`

Run: `npm --prefix frontend run typecheck`

Run: `npm --prefix frontend run build`

Expected: 全部退出码 0；既有 Vite chunk 警告可记录但不得当作失败隐藏。

- [ ] **Step 2: 执行项目固定入口**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope frontend -ReportKey product-library-card-density -OwnedFiles 'frontend/src/components/product/ProductSelectionCard.vue;frontend/src/components/product/ProductSelectionCard.test.ts;frontend/src/views/product/ProductLibrary.vue;frontend/src/views/product/ProductLibrary.test.ts;frontend/src/views/product/product-library-layout.ts;frontend/src/views/product/product-library-layout.test.ts;docs/superpowers/specs/2026-07-16-product-library-card-density-design.md;docs/superpowers/plans/2026-07-16-product-library-card-density.md' -Message 'fix(product-ui): compact product cards and show full identity'
```

Expected: 前端构建通过、`frontend-real-pre` 重启、`/healthz` 可访问、稳定 evidence 生成。

- [ ] **Step 3: 浏览器验证**

在本地 real-pre 商品库验证：卡片间距为 8px；64 字符名称与 19 字符 ID 完整可见；快捷按钮只有复制 ID；复制值准确；Console 无关键错误；悬浮“复制简介 / 快速寄样”和点击卡片进详情仍可用。

- [ ] **Step 4: 执行收尾门禁**

Run: `powershell -ExecutionPolicy Bypass -File harness/scripts/check-harness-limits.ps1 -BaselineRef HEAD`

Run: `git diff --check`

Expected: 当前任务 `TASK_GATE=PASS`，diff check 无输出；仓库历史健康问题单独记录。
