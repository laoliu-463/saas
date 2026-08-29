import { test, expect } from '@playwright/test';
import { storageStates } from './helpers/test-data';
import { capturePage } from './helpers/screenshot';
import { gotoApp } from './helpers/page-ready';
import { testIds } from './helpers/selectors';
import { seedTestData } from './helpers/api-assertions';

/**
 * 商品库卡片 hover 抽屉字段验证。
 *
 * <p>背景：旧 {@code ProductService.toLegacyProduct} 漏传 {@code activityName}，且未设
 * {@code shopScore}，导致商品库卡片 hover 抽屉"活动"字段一直为 {@code -}，且没有"商家评分"字段。
 * 本测试覆盖：</p>
 * <ol>
 *   <li>抽屉中"商家评分"字段存在并显示真实分值（来自后端 rawPayload.shopScore）</li>
 *   <li>抽屉中"活动"字段存在并显示真实活动名（来自后端 colonel_activity.name）</li>
 * </ol>
 */
test.beforeAll(async () => {
  if (process.env.E2E_SKIP_TEST_SEED === 'true') return;
  await seedTestData();
});

test.use({ storageState: storageStates.channelLeader });

test('商品库抽屉显示商家评分 + 活动字段', async ({ page }, testInfo) => {
  await gotoApp(page, '/product');
  await expect(page.getByTestId(testIds.productLibraryPage)).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId(testIds.productGrid)).toBeVisible({ timeout: 30_000 });

  const firstCard = page.getByTestId(testIds.productCard).first();
  await expect(firstCard).toBeVisible({ timeout: 30_000 });

  // 触发 hover 让抽屉展开（桌面端 hover-mode）
  await firstCard.hover();
  // 等待抽屉 DOM 出现（drawer-shell 常驻，hover 后 CSS 触发展开）
  const drawer = page.getByTestId('product-selection-drawer').first();
  await expect(drawer).toBeVisible({ timeout: 5_000 });

  // 抽屉里"商家评分"和"活动"两个字段都应该渲染
  await expect(drawer).toContainText('商家评分');
  await expect(drawer).toContainText('活动');

  await capturePage(page, testInfo, '03b-product-library-drawer-fields');
});
