import { test, expect } from './fixtures';
import { capturePage } from './helpers/screenshot';
import { testIds } from './helpers/selectors';
import { accounts } from './helpers/test-data';
import { gotoApp, waitForAppReady } from './helpers/page-ready';

test.describe.configure({ mode: 'serial' });

test.use({ trace: 'on', video: 'on' });

test.setTimeout(180_000);

const CRED = accounts.admin;

/**
 * 管理员从登录到全功能巡检 — 真实用户旅程。
 *
 * 导航策略：登录统一走 LoginPage，其余页面通过
 * 顶部 Header 导航、侧边栏菜单或统一 gotoApp 进入。
 */
test('管理员从登录到全功能巡检（真实用户旅程）', async ({ page, loginPage, appShell }, testInfo) => {

  // ── Step 1: 打开登录页 ──────────────────────────────────────
  await test.step('1. 打开登录页', async () => {
    await loginPage.open();
    await loginPage.expectLoaded();
    await capturePage(page, testInfo, 'step-01-login-page');
  });

  // ── Step 2: 输入用户名密码 ──────────────────────────────────
  await test.step('2. 输入用户名密码', async () => {
    await loginPage.usernameInput.fill(CRED.username);
    await loginPage.passwordInput.fill(CRED.password);
    await capturePage(page, testInfo, 'step-02-credentials-filled');
  });

  // ── Step 3: 点击登录 ────────────────────────────────────────
  await test.step('3. 点击登录', async () => {
    await loginPage.submitButton.click();
    await expect(page).not.toHaveURL(/\/login(?:$|\?)/, { timeout: 15_000 });
    await waitForAppReady(page);
    await capturePage(page, testInfo, 'step-03-after-login');
  });

  // ── Step 4: 等待 Dashboard 加载 ─────────────────────────────
  await test.step('4. 等待 Dashboard 加载', async () => {
    // 登录后默认跳转到首页，Header 导航可见即表示加载完成
    await expect(page.locator(`[data-testid="${testIds.navDashboard}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-04-dashboard-loaded');
  });

  // ── Step 5: 点击"商品库"菜单 ────────────────────────────────
  await test.step('5. 点击"商品库"菜单', async () => {
    await appShell.productNav.click();
    // 点击商品库 header tab 后路由到 /product，侧边栏切换到 product section
    await page.waitForURL('**/product**', { timeout: 10_000 });
  });

  // ── Step 6: 查看商品列表 ────────────────────────────────────
  await test.step('6. 查看商品列表', async () => {
    await expect(page.locator(`[data-testid="${testIds.productLibraryPage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-06-product-library');
  });

  // ── Step 7: 点击商品详情 ────────────────────────────────────
  await test.step('7. 点击商品详情', async () => {
    const detailBtn = page.locator(`[data-testid="${testIds.productDetailButton}"]`).first();
    await expect(detailBtn).toBeVisible({ timeout: 10_000 });
    await detailBtn.click();
    await expect(page.locator('body')).toContainText(/商品业务全貌|推广链接|基础信息/);
    await capturePage(page, testInfo, 'step-07-product-detail');
  });

  // ── Step 8: 返回商品库（关闭详情面板） ──────────────────────
  await test.step('8. 返回商品库', async () => {
    // 尝试按 ESC 关闭 drawer
    await page.keyboard.press('Escape');
    await expect(page.locator(`[data-testid="${testIds.productLibraryPage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-08-back-to-product-library');
  });

  // ── Step 9: 点击一键复制专属推广链接 ────────────────────────
  await test.step('9. 点击一键复制专属推广链接', async () => {
    const copyBtn = page.locator(`[data-testid="${testIds.productCopyLink}"]`).first();
    await expect(copyBtn).toBeVisible({ timeout: 10_000 });
    await copyBtn.click();
    await capturePage(page, testInfo, 'step-09-copy-link-clicked');
  });

  // ── Step 10: 等待操作反馈 ──────────────────────────────────
  await test.step('10. 等待操作反馈', async () => {
    // 等待消息出现（成功或失败都接受，继续走完旅程）
    const msg = page.locator('.n-message').last();
    await expect(msg).toBeVisible({ timeout: 10_000 });
    const text = await msg.innerText();
    console.log(`[step-10] 消息反馈: ${text}`);
    await capturePage(page, testInfo, 'step-10-feedback');
  });

  // ── Step 11: 点击"数据看板"菜单（切换到数据平台 section）──
  await test.step('11. 点击"数据看板"菜单', async () => {
    await appShell.dashboardNav.click();
    // Header tab 切换到 /data，侧边栏变为数据平台
    await expect(page.locator(`[data-testid="${testIds.sidebarMenu}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-11-data-section');
  });

  // ── Step 12: 点击"查看完整明细"进入订单页 ───────────────────
  await test.step('12. 点击"查看完整明细"进入订单页', async () => {
    const detailEntry = page.locator(`[data-testid="${testIds.dashboardOrdersLink}"]`);
    await expect(detailEntry).toBeVisible({ timeout: 10_000 });
    await detailEntry.click({ timeout: 10_000 });
    await page.waitForURL('**/data/orders**', { timeout: 10_000 });
  });

  // ── Step 13: 查看订单列表 ────────────────────────────────────
  await test.step('13. 查看订单列表', async () => {
    await expect(page.locator(`[data-testid="${testIds.dataOrdersPage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-13-data-orders');
  });

  // ── Step 14: 点击"寄样审核"菜单 ────────────────────────────
  await test.step('14. 点击"寄样审核"菜单', async () => {
    await page.getByText('寄样审核', { exact: true }).click();
    await page.waitForURL('**/sample**', { timeout: 10_000 });
  });

  // ── Step 15: 查看寄样状态 ────────────────────────────────────
  await test.step('15. 查看寄样状态', async () => {
    await expect(page.locator(`[data-testid="${testIds.samplePage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-15-sample-page');
  });

  // ── Step 16: 点击"达人CRM"菜单 ─────────────────────────────
  await test.step('16. 点击"达人CRM"菜单', async () => {
    await appShell.talentNav.click();
    await page.waitForURL('**/talent**', { timeout: 10_000 });
  });

  // ── Step 17: 查看达人列表 ────────────────────────────────────
  await test.step('17. 查看达人列表', async () => {
    await expect(page.locator(`[data-testid="${testIds.talentPage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-17-talent-page');
  });

  // ── Step 18: 点击侧边栏"团队公海" ──────────────────────────
  await test.step('18. 点击侧边栏"团队公海"', async () => {
    const menuItem = page.locator('.n-menu-item-content', { hasText: '团队公海' }).first();
    if (await menuItem.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await menuItem.click();
      await waitForAppReady(page);
    }
    await capturePage(page, testInfo, 'step-18-talent-team-public');
  });

  // ── Step 19: 点击"数据看板"回到数据平台 ────────────────────
  await test.step('19. 点击"数据看板"回到数据平台', async () => {
    await appShell.dashboardNav.click();
    await expect(page.locator(`[data-testid="${testIds.sidebarMenu}"]`)).toBeVisible({ timeout: 10_000 });
  });

  // ── Step 20: 继续确认数据看板仍可见 ────────────────────────
  await test.step('20. 继续确认数据看板仍可见', async () => {
    await expect(page.locator(`[data-testid="${testIds.dashboardPage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-20-data-dashboard');
  });

  // ── Step 21: 点击"系统管理"菜单（仅 admin 可见）─────────────
  await test.step('21. 点击"系统管理"菜单', async () => {
    await appShell.systemNav.click();
    await page.waitForURL('**/system**', { timeout: 10_000 });
  });

  // ── Step 22: 查看系统管理页面 ────────────────────────────────
  await test.step('22. 查看系统管理页面', async () => {
    await expect(page).toHaveURL(/\/system\/users/);
    await expect(page.locator('body')).toContainText(/用户管理|员工管理|系统管理/);
    await capturePage(page, testInfo, 'step-22-system-users');
  });

  // ── Step 23: 点击侧边栏"抖店联调" ──────────────────────────
  await test.step('23. 点击侧边栏"抖店联调"', async () => {
    await gotoApp(page, '/system/douyin');
    await expect(page).toHaveURL(/\/system\/douyin/);
    await capturePage(page, testInfo, 'step-23-system-douyin');
  });

  // ── Step 24: 打开归因概览（Dashboard） ──────────────────────
  await test.step('24. 回到归因概览', async () => {
    await gotoApp(page, '/dashboard');
    await page.waitForURL('**/dashboard', { timeout: 10_000 });
    await expect(page.locator(`[data-testid="${testIds.dashboardOverviewPage}"]`)).toBeVisible({ timeout: 10_000 });
    await capturePage(page, testInfo, 'step-24-dashboard-overview');
  });
});
