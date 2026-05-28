import { expect, test, type Page, type Route } from '@playwright/test'
import { readFixture } from './helpers/fixtures'
import { storageStates } from './helpers/test-data'

test.use({ storageState: storageStates.channelLeader })

const talentListFixture = readFixture<{ records: any[]; total: number; page: number; size: number }>(
  'talent',
  'single.json'
)

const batchImportSuccess = {
  code: 200,
  data: {
    total: 2,
    created: 1,
    skipped: 0,
    failed: 1,
    items: [
      { account: '111', status: 'CREATED', talentId: 't-1', message: null },
      { account: 'bad', status: 'FAILED', talentId: null, message: '无法解析达人账号' }
    ]
  }
}

const presetTags = {
  code: 200,
  data: ['高意向', '已合作', '待跟进']
}

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data)
  })
}

async function mockTalentApis(page: Page) {
  await page.route('**/api/talents**', async (route) => {
    const url = new URL(route.request().url())
    const method = route.request().method()

    if (method === 'GET' && url.pathname.endsWith('/api/talents/preset-tags')) {
      await fulfillJson(route, presetTags)
      return
    }

    if (method === 'POST' && url.pathname.endsWith('/api/talents/batch-import')) {
      await fulfillJson(route, batchImportSuccess)
      return
    }

    if (method === 'GET' && url.pathname.endsWith('/api/talents')) {
      await fulfillJson(route, { code: 200, data: talentListFixture })
      return
    }

    if (method === 'GET' && /\/api\/talents\/[^/]+$/.test(url.pathname)) {
      await fulfillJson(route, {
        code: 200,
        data: {
          talent: {
            id: 'talent-1',
            nickname: '测试达人',
            douyinNo: '111',
            dataSource: 'CRAWLER',
            syncStatus: 'success',
            tags: []
          },
          claim: { poolStatus: 'PRIVATE', ownerName: '组长' },
          samples: [],
          orders: []
        }
      })
      return
    }

    if (method === 'POST' && url.pathname.endsWith('/sync-profile')) {
      await fulfillJson(route, {
        code: 200,
        data: {
          success: false,
          provider: 'API',
          syncErrorCode: 'NOT_CONFIGURED',
          syncErrorMessage: '抖店 Token 未配置',
          dataSource: 'CRAWLER'
        }
      })
      return
    }

    await route.continue()
  })
}

test.describe('达人域 V1.1 收尾', () => {
  test.beforeEach(async ({ page }) => {
    await mockTalentApis(page)
    await page.goto('/talent')
    await expect(page.getByTestId('talent-page')).toBeVisible({ timeout: 30_000 })
  })

  test('打开批量导入弹窗并拦截非法文件', async ({ page }) => {
    await page.getByTestId('talent-batch-import').click()
    await expect(page.getByTestId('talent-batch-import-paste')).toBeVisible()

    const fileInput = page.getByTestId('talent-batch-import-file')
    await fileInput.setInputFiles({
      name: 'invalid.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('not-a-talent-file')
    })
    await expect(page.locator('body')).toContainText('仅支持')
  })

  test('批量导入展示部分失败明细', async ({ page }) => {
    await page.getByTestId('talent-batch-import').click()
    await page.getByTestId('talent-batch-import-paste').locator('textarea').fill('111\nbad')
    await page.getByTestId('talent-batch-import-submit').click()
    await expect(page.getByTestId('talent-batch-import-result')).toContainText('失败 1')
    await expect(page.getByTestId('talent-batch-import-failures')).toBeVisible()
  })

  test('详情刷新失败时保留原因提示', async ({ page }) => {
    await page.getByRole('button', { name: '查看详情' }).first().click()
    await expect(page.getByText('测试达人')).toBeVisible({ timeout: 15_000 })
    await page.getByTestId('talent-detail-refresh').click()
    await expect(page.locator('body')).toContainText('Token 未配置')
  })

  test('预设标签多选最多 3 个', async ({ page }) => {
    await page.getByRole('button', { name: '查看详情' }).first().click()
    await page.getByTestId('talent-edit-tags').click()
    const select = page.getByTestId('talent-preset-tags-select')
    await expect(select).toBeVisible()
    await select.click()
    await page.getByText('高意向').click()
    await page.getByText('已合作').click()
    await page.getByText('待跟进').click()
    await expect(select.locator('.n-tag')).toHaveCount(3)
  })
})
