import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

const srcRoot = join(process.cwd(), 'src')

const sourceFiles = collectSourceFiles(srcRoot)

function collectSourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const absolutePath = join(dir, entry.name)
    if (entry.isDirectory()) {
      return collectSourceFiles(absolutePath)
    }
    if (!/\.(ts|vue)$/.test(entry.name)) {
      return []
    }
    if (/\.(test|spec)\.ts$/.test(entry.name) || /\.d\.ts$/.test(entry.name)) {
      return []
    }
    return [absolutePath]
  })
}

function toRepoPath(absolutePath: string) {
  return `frontend/src/${relative(srcRoot, absolutePath).replace(/\\/g, '/')}`
}

function matchingFiles(patterns: RegExp[]) {
  return sourceFiles
    .filter((file) => {
      const text = readFileSync(file, 'utf8')
      return patterns.some((pattern) => pattern.test(text))
    })
    .map(toRepoPath)
    .sort()
}

function findings(patterns: Array<[string, RegExp]>) {
  return sourceFiles
    .flatMap((file) => {
      const text = readFileSync(file, 'utf8')
      return patterns
        .filter(([, pattern]) => pattern.test(text))
        .map(([name]) => `${toRepoPath(file)}::${name}`)
    })
    .sort()
}

describe('frontend business rule boundary scan', () => {
  it('does not directly call third-party APIs or persistence APIs from frontend code', () => {
    expect(findings([
      ['request-http', /\brequest\.(?:get|post|put|delete|patch)\(\s*['"`]https?:\/\//],
      ['axios-http', /\baxios\.(?:get|post|put|delete|patch)\(\s*['"`]https?:\/\//],
      ['fetch-http', /\bfetch\(\s*['"`]https?:\/\//],
      ['douyin-sdk-method', /\bbuyin\.[A-Za-z0-9_]+\s*\(/],
      ['sql-select', /\bSELECT\s+\*/i],
      ['sql-insert', /\bINSERT\s+INTO\b/i],
      ['sql-update', /\bUPDATE\s+\w+\s+SET\b/i],
      ['sql-delete', /\bDELETE\s+FROM\b/i],
      ['jdbc-template', /\bJdbcTemplate\b/]
    ])).toEqual([])
  })

  it('keeps frontend permission and action-decision hotspots explicitly inventoried', () => {
    expect(matchingFiles([
      /\bhasAccess\s*\(/,
      /\bROLE_CODES\b/,
      /\broleCodes\.includes\s*\(/,
      /\bcanExport[A-Za-z]*\b/,
      /\bgetProductActions\s*\(/
    ])).toEqual([
      'frontend/src/constants/rbac.ts',
      'frontend/src/main.ts',
      'frontend/src/router/guard.ts',
      'frontend/src/router/index.ts',
      'frontend/src/router/menuTree.ts',
      'frontend/src/router/navigation.ts',
      'frontend/src/stores/auth.ts',
      'frontend/src/views/dashboard/index.vue',
      'frontend/src/views/data/OrderDetailTab.vue',
      'frontend/src/views/data/OrderList.vue',
      'frontend/src/views/data/index.vue',
      'frontend/src/views/layout/Header.vue',
      'frontend/src/views/layout/Sider.vue',
      'frontend/src/views/product/ActivityList.vue',
      'frontend/src/views/product/ProductDetail.vue',
      'frontend/src/views/product/ProductLibrary.vue',
      'frontend/src/views/product/components/ProductActionColumn.vue',
      'frontend/src/views/product/index.vue',
      'frontend/src/views/product/product-actions.ts',
      'frontend/src/views/sample/CooperationWorkbench.vue',
      'frontend/src/views/sample/SampleDetail.vue',
      'frontend/src/views/sample/sample-permissions.ts',
      'frontend/src/views/system/RoleList.vue',
      'frontend/src/views/talent/components/TalentDetailModal.vue',
      'frontend/src/views/talent/constants.ts',
      'frontend/src/views/talent/index.vue'
    ])
  })

  it('keeps frontend status mappings and money display fields explicitly inventoried', () => {
    expect(matchingFiles([
      /\b(?:PENDING_AUDIT|PENDING_SHIP|REJECTED|APPROVED|FINISHED|COMPLETED|CLOSED|PROMOTING)\b/,
      /\b(?:ATTRIBUTED|UNATTRIBUTED)\b/,
      /\b(?:commissionRate|serviceFeeRate|grossProfit|settlementAmount|payAmount|orderAmount|refundAmount)\b/
    ])).toEqual([
      'frontend/src/api/data.ts',
      'frontend/src/api/order.ts',
      'frontend/src/api/performance.ts',
      'frontend/src/api/talent.ts',
      'frontend/src/components/StatusTag.vue',
      'frontend/src/components/product/ProductSelectionCard.vue',
      'frontend/src/constants/orderAttribution.ts',
      'frontend/src/types/index.ts',
      'frontend/src/types/productManage.ts',
      'frontend/src/views/dashboard/index.vue',
      'frontend/src/views/data/OrderDetailTab.vue',
      'frontend/src/views/data/OrderList.vue',
      'frontend/src/views/data/index.vue',
      'frontend/src/views/orders/components/OrderDetailModal.vue',
      'frontend/src/views/orders/index.vue',
      'frontend/src/views/product/ProductDetail.vue',
      'frontend/src/views/product/ProductLibrary.vue',
      'frontend/src/views/product/activity-list-display.ts',
      'frontend/src/views/product/activity-product-status-display.ts',
      'frontend/src/views/product/components/ProductCard.vue',
      'frontend/src/views/product/index.vue',
      'frontend/src/views/product/product-actions.ts',
      'frontend/src/views/product/product-filters.ts',
      'frontend/src/views/product/product-library-display.ts',
      'frontend/src/views/product/product-operation-log-display.ts',
      'frontend/src/views/sample/CooperationWorkbench.vue',
      'frontend/src/views/sample/SampleDetail.vue',
      'frontend/src/views/sample/sample-permissions.ts',
      'frontend/src/views/system/components/PromotionTemplateEditor.vue',
      'frontend/src/views/talent/components/TalentDetailModal.vue'
    ])
  })

  it('limits money arithmetic to display formatting and table sorting helpers', () => {
    expect(matchingFiles([
      /\b(?:commissionRate|serviceFeeRate|grossProfit|settlementAmount|payAmount|orderAmount|refundAmount)\b.*[+\-*/]/,
      /[+\-*/].*\b(?:commissionRate|serviceFeeRate|grossProfit|settlementAmount|payAmount|orderAmount|refundAmount)\b/
    ])).toEqual([
      'frontend/src/components/product/ProductSelectionCard.vue',
      'frontend/src/views/dashboard/index.vue',
      'frontend/src/views/data/OrderDetailTab.vue',
      'frontend/src/views/data/OrderList.vue',
      'frontend/src/views/data/index.vue',
      'frontend/src/views/orders/components/OrderDetailModal.vue',
      'frontend/src/views/orders/index.vue',
      'frontend/src/views/product/ProductDetail.vue'
    ])
  })
})
