import { expect, request as playwrightRequest, test, type APIRequestContext, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { showStepBanner, visibleClick, visibleFill, visiblePause } from './helpers/visual-journey';

type JsonMap = Record<string, unknown>;

interface AuthState {
  token: string;
  refreshToken: string;
  user: JsonMap;
  userId: string;
  username: string;
  roleCodes: string[];
  dataScope: unknown;
  deptId: string | null;
}

interface StepResult {
  id: string;
  title: string;
  status: 'PASS' | 'FAIL';
  details?: JsonMap;
  error?: string;
}

interface RoleFlowAction {
  at: string;
  title: string;
  status: 'PASS' | 'FAIL';
  details?: JsonMap;
  error?: string;
}

interface RoleFlow {
  role: string;
  username: string;
  startedAt: string;
  finishedAt?: string;
  actions: RoleFlowAction[];
}

interface RunState {
  startedAt: string;
  finishedAt?: string;
  evidenceDir: string;
  screenshotsDir: string;
  screenshotSeq: number;
  steps: StepResult[];
  failures: string[];
  warnings: string[];
  roleFlows: Record<string, RoleFlow>;
  flow: JsonMap;
}

interface RoleSession {
  context: BrowserContext;
  page: Page;
  auth: AuthState;
  username: string;
}

interface RawApiResult {
  status: number;
  ok: boolean;
  body: unknown;
  durationMs: number;
}

const BACKEND = (process.env.E2E_BACKEND_URL || process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
const FRONTEND = (process.env.E2E_BASE_URL || process.env.FRONTEND_URL || 'http://localhost:3000').replace(/\/$/, '');
const ACTIVITY_ID = process.env.E2E_JOURNEY_ACTIVITY_ID || process.env.E2E_ROLE_ACTIVITY_ID || '3916506';
const PREFERRED_PRODUCT_ID = process.env.E2E_JOURNEY_PRODUCT_ID || process.env.E2E_PRODUCT_ID || '3810562766247428542';
const SHOULD_RUN =
  process.env.E2E_REAL_PRE_JOURNEY_VISUAL === 'true' ||
  process.env.npm_lifecycle_event === 'e2e:real-pre:journey:visual' ||
  process.argv.some((arg) => arg.includes('12-real-pre-full-business-journey.visual'));
const EVIDENCE_DIR = process.env.E2E_JOURNEY_EVIDENCE_DIR || join(
  process.cwd(),
  'runtime',
  'qa',
  'out',
  `real-pre-full-business-journey-${formatLocalTimestamp(new Date())}`
);
const DEFAULT_PASSWORD = 'admin123';
const VIEWPORT = { width: 1440, height: 900 } as const;
const DOUYIN_ONE_CLICK_CHECKS = [
  /^Token 正常$/,
  /^授权主体正常$/,
  /^(活动商品已刷新|活动商品刷新为空)$/,
  /^(商品 SKU 已验证|商品 SKU 待补样本|商品 SKU 已跳过)$/,
  /^(订单同步成功|订单同步有失败)$/,
  /^(Dashboard 已读取真实订单|Dashboard 当前无订单)$/
] as const;
const USERNAMES = {
  admin: 'admin',
  bizLeader: 'biz_leader',
  merchant: 'biz_staff',
  channel: 'channel_staff',
  operator: 'ops_staff',
  channelLeader: 'channel_leader'
} as const;
const ROLE_LABELS: Record<string, string> = {
  admin: '管理员',
  'biz-leader': '招商组长',
  merchant: '招商账号',
  channel: '渠道账号',
  operator: '运营账号',
  'channel-leader': '渠道组长',
  cleanup: '管理员最终复核'
};
const USER_ROLE_LABELS: Record<string, string> = {
  [USERNAMES.admin]: '管理员',
  [USERNAMES.bizLeader]: '招商组长',
  [USERNAMES.merchant]: '招商账号',
  [USERNAMES.channel]: '渠道账号',
  [USERNAMES.operator]: '运营账号',
  [USERNAMES.channelLeader]: '渠道组长'
};

test.describe.configure({ mode: 'serial' });

test('real-pre full business journey visual handoff keeps one runId moving across roles', async ({ browser }, testInfo) => {
  test.skip(!SHOULD_RUN, 'Run with npm run e2e:real-pre:journey:visual or set E2E_REAL_PRE_JOURNEY_VISUAL=true.');
  test.setTimeout(900_000);

  const run = createRunState();
  const api = await playwrightRequest.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });

  try {
    await criticalStep(run, '00-env', 'real-pre environment guard', async () => verifyEnvironment(api, run));
    await criticalStep(run, '01-admin-initial', 'admin config CRUD, system review, and douyin refresh', async () => runAdminInitial(browser, api, run));
    await criticalStep(run, '02-biz-leader', 'biz leader syncs activity data and assigns auditor', async () => runBizLeader(browser, api, run));
    await criticalStep(run, '03-merchant-product', 'merchant audits product and enriches business notes', async () => runMerchantProduct(browser, api, run));
    await criticalStep(run, '04-channel', 'channel completes talent CRUD, promotion link, and sample submit', async () => runChannel(browser, api, run));
    await criticalStep(run, '05-merchant-sample', 'merchant revisits to approve sample into shipment queue', async () => runMerchantSample(browser, api, run));
    await criticalStep(run, '06-operator', 'operator ships and signs the sample into pending task', async () => runOperator(browser, api, run));
    await criticalStep(run, '07-channel-leader', 'channel leader verifies team visibility and boundary checks', async () => runChannelLeader(browser, api, run));
    await criticalStep(run, '08-admin-final', 'admin reviews global evidence and performs safe cleanup', async () => runAdminFinal(browser, api, run));
  } finally {
    await api.dispose();
    run.finishedAt = new Date().toISOString();
    flushEvidence(run);
  }

  await testInfo.attach('real-pre-full-business-journey-summary.json', {
    body: JSON.stringify(buildSummary(run), null, 2),
    contentType: 'application/json'
  });

  expect(run.failures, `real-pre full business journey failures:\n${run.failures.join('\n')}`).toEqual([]);
});

function createRunState(): RunState {
  mkdirSync(EVIDENCE_DIR, { recursive: true });
  const screenshotsDir = join(EVIDENCE_DIR, 'screenshots');
  mkdirSync(screenshotsDir, { recursive: true });
  const now = new Date();
  const runStamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  return {
    startedAt: now.toISOString(),
    evidenceDir: EVIDENCE_DIR,
    screenshotsDir,
    screenshotSeq: 0,
    steps: [],
    failures: [],
    warnings: [],
    roleFlows: {},
    flow: {
      evidenceType: 'real-pre-full-business-journey-visual',
      visualMode: 'headed browser director mode',
      headed: true,
      slowMoMs: Number(process.env.PW_SLOWMO_MS || 0),
      stepPauseMs: Number(process.env.PW_STEP_PAUSE_MS || 800),
      afterActionPauseMs: Number(process.env.PW_AFTER_ACTION_PAUSE_MS || 600),
      viewport: `${VIEWPORT.width}x${VIEWPORT.height}`,
      runId: `QA${runStamp}`,
      runStamp,
      activityId: ACTIVITY_ID,
      requestedProductId: PREFERRED_PRODUCT_ID,
      selectedProductId: '',
      productLocalId: '',
      productTitle: '',
      configKey: '',
      configId: '',
      configValue: '',
      talentName: '',
      talentUid: '',
      talentId: '',
      tempTalentUid: '',
      tempTalentId: '',
      tempTalentRetiredBy: '',
      pickSource: '',
      mappingCount: 0,
      sampleId: '',
      sampleRequestNo: '',
      sampleStatus: '',
      trackingNo: '',
      currentRole: '',
      currentAction: '',
      databaseCleared: false,
      secretsWrittenToDocsOrGit: false
    }
  };
}

async function criticalStep(
  run: RunState,
  id: string,
  title: string,
  fn: () => Promise<JsonMap | void>
): Promise<void> {
  await test.step(`${id} ${title}`, async () => {
    try {
      const details = await fn();
      run.steps.push({ id, title, status: 'PASS', details: details || {} });
      flushEvidence(run);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      run.steps.push({ id, title, status: 'FAIL', error: message });
      run.failures.push(`${id} ${title}: ${message}`);
      flushEvidence(run);
      throw error;
    }
  });
}

function beginRoleFlow(run: RunState, key: string, username: string): RoleFlow {
  const existing = run.roleFlows[key];
  if (existing) {
    existing.username = username;
    if (!existing.startedAt) existing.startedAt = new Date().toISOString();
    return existing;
  }
  const flow: RoleFlow = {
    role: key,
    username,
    startedAt: new Date().toISOString(),
    actions: []
  };
  run.roleFlows[key] = flow;
  flushEvidence(run);
  return flow;
}

function finishRoleFlow(run: RunState, key: string): void {
  const flow = run.roleFlows[key];
  if (flow) {
    flow.finishedAt = new Date().toISOString();
    flushEvidence(run);
  }
}

async function roleAction(
  run: RunState,
  roleKey: string,
  title: string,
  fn: () => Promise<JsonMap | void>
): Promise<JsonMap> {
  return await test.step(`${ROLE_LABELS[roleKey] || roleKey}：${title}`, async () => {
    run.flow.currentRole = roleKey;
    run.flow.currentAction = title;
    flushEvidence(run);
    const flow = run.roleFlows[roleKey];
    try {
      const details = await fn();
      flow.actions.push({
        at: new Date().toISOString(),
        title,
        status: 'PASS',
        details: details || {}
      });
      flushEvidence(run);
      return details || {};
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      flow.actions.push({
        at: new Date().toISOString(),
        title,
        status: 'FAIL',
        error: message
      });
      flushEvidence(run);
      throw new Error(`${roleKey}/${title}: ${message}`);
    }
  });
}

async function verifyEnvironment(api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const env = await apiSuccess(api, 'GET', '/api/system/env');
  const health = await rawApi(api, 'GET', '/api/actuator/health');
  const envData = unwrap(env.body) as JsonMap;
  assertTrue(
    String(envData.environmentLabel || '').toUpperCase() === 'REAL-PRE' ||
      asStringArray(envData.activeProfiles).includes('real-pre'),
    `Expected REAL-PRE environment, got ${JSON.stringify(envData)}`
  );
  assertTrue(envData.appTestEnabled === false, 'APP_TEST_ENABLED must be false');
  assertTrue(envData.douyinTestEnabled === false, 'DOUYIN_TEST_ENABLED must be false');
  assertTrue(health.ok && (health.body as JsonMap | undefined)?.status === 'UP', 'actuator health must be UP');
  run.flow.environmentLabel = envData.environmentLabel || 'REAL-PRE';
  return {
    environment: envData,
    health: health.body as JsonMap
  };
}

async function runAdminInitial(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.admin, run);
  beginRoleFlow(run, 'admin', session.username);
  try {
    const login = await roleAction(run, 'admin', '管理员登录与系统入口确认', async () => {
      assertTrue(session.auth.roleCodes.includes('admin'), 'admin role should be present');
      await waitForIdle(session.page);
      await expect(session.page.locator('.top-header')).toBeVisible({ timeout: 20_000 });
      const path = currentPath(session.page);
      await expect(session.page.locator('[data-testid="nav-system"]')).toBeVisible();
      const screenshot = await capture(run, session.page, 'admin-home');
      return {
        username: session.auth.username,
        roleCodes: session.auth.roleCodes,
        currentPath: path,
        screenshot
      };
    });

    const configCrud = await roleAction(run, 'admin', '管理员新增并修改 QA 配置项', async () =>
      createAndUpdateJourneyConfig(session.page, api, session.auth, run)
    );

    const douyinRefresh = await roleAction(run, 'admin', '管理员执行抖店联调一键刷新', async () =>
      verifyDouyinOneClick(session.page, run)
    );

    const logout = await roleAction(run, 'admin', '管理员退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/users?page=1&size=1')
    );

    return { login, configCrud, douyinRefresh, logout };
  } finally {
    finishRoleFlow(run, 'admin');
    await session.context.close().catch(() => undefined);
  }
}

async function runBizLeader(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.bizLeader, run);
  beginRoleFlow(run, 'biz-leader', session.username);
  try {
    const landing = await roleAction(run, 'biz-leader', '招商组长登录、菜单和越权路由校验', async () => {
      await waitForIdle(session.page);
      await expect(session.page.locator('[data-testid="nav-activity-product"]')).toBeVisible();
      await assertRouteRedirect(session.page, '/system/config');
      await navigate(session.page, '/product/manage');
      const screenshot = await capture(run, session.page, 'biz-leader-activity-list');
      return {
        currentPath: currentPath(session.page),
        screenshot
      };
    });

    const assign = await roleAction(run, 'biz-leader', '招商组长同步活动商品并分配审核人', async () => {
      await showStepBanner(session.page, `【招商组长】同步/查看活动商品\n活动 ID：${ACTIVITY_ID}`);
      const activities = await apiSuccess(api, 'GET', '/api/colonel/activities', session.auth, {
        params: { page: 1, size: 10 }
      });
      const productList = await apiSuccessWithRetry(
        api,
        'GET',
        `/api/colonel/activities/${ACTIVITY_ID}/products`,
        session.auth,
        {
          params: { count: 20, refresh: true }
        },
        {
          attempts: 4,
          delayMs: 3_000
        }
      );
      const products = extractRecords(productList.body);
      assertTrue(products.length > 0, 'activity product sync should return products');
      const candidate = selectPendingAuditProduct(products);
      const productId = String(candidate.productId ?? candidate.product_id);
      const productInitialStatus = String(candidate.bizStatus || 'PENDING_AUDIT');
      const requiresAudit = productInitialStatus === 'PENDING_AUDIT';
      let detail = candidate;
      let assignee: JsonMap | null = null;
      if (requiresAudit) {
        assignee = await firstAssignableUser(api, session.auth, 'biz_staff');
        const assigned = await apiSuccess(
          api,
          'PUT',
          `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/audit-assignee`,
          session.auth,
          { data: { assigneeId: assignee.id } }
        );
        detail = unwrap(assigned.body) as JsonMap;
      } else {
        const current = await apiSuccess(api, 'GET', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}`, session.auth);
        detail = unwrap(current.body) as JsonMap;
      }
      run.flow.selectedProductId = productId;
      run.flow.productInitialStatus = productInitialStatus;
      run.flow.productAuditRequired = requiresAudit;
      run.flow.productTitle = String(detail.title || candidate.title || candidate.productName || productId);
      run.flow.auditAssigneeId = assignee ? String(assignee.id) : '';
      run.flow.auditAssigneeUsername = assignee ? String(assignee.username || assignee.realName || 'biz_staff') : '复用已入库商品';
      await navigate(session.page, `/product/manage/${ACTIVITY_ID}`);
      await showStepBanner(
        session.page,
        requiresAudit
          ? `【招商组长】分配商品给招商\n商品：${productId}\n状态：PENDING_AUDIT -> 已指定审核人 ${run.flow.auditAssigneeUsername}`
          : `【招商组长】当前活动暂无待审商品，复用业务就绪商品继续演示\n商品：${productId}\n当前状态：${productInitialStatus}`
      );
      const screenshot = await capture(run, session.page, 'biz-leader-activity-products');
      return {
        activities: summarizeResult(activities),
        productCount: products.length,
        productId,
        productTitle: run.flow.productTitle,
        initialStatus: productInitialStatus,
        productAuditRequired: requiresAudit,
        assignedTo: run.flow.auditAssigneeUsername,
        screenshot
      };
    });

    const logout = await roleAction(run, 'biz-leader', '招商组长退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/colonel/activities?page=1&size=1')
    );

    return { landing, assign, logout };
  } finally {
    finishRoleFlow(run, 'biz-leader');
    await session.context.close().catch(() => undefined);
  }
}

async function runMerchantProduct(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.merchant, run);
  beginRoleFlow(run, 'merchant', session.username);
  try {
    const landing = await roleAction(run, 'merchant', '招商登录、我的商品页和越权校验', async () => {
      await waitForIdle(session.page);
      await assertRouteRedirect(session.page, '/talent');
      await navigate(session.page, '/product/manage/products');
      const screenshot = await capture(run, session.page, 'merchant-product-manage');
      return {
        currentPath: currentPath(session.page),
        screenshot
      };
    });

    const audit = await roleAction(run, 'merchant', '招商审核商品并补充推广资料', async () => {
      const productId = requireFlowString(run, 'selectedProductId');
      const initialStatus = String(run.flow.productInitialStatus || '');
      const requiresAudit = Boolean(run.flow.productAuditRequired);
      let approvedData: JsonMap;
      if (requiresAudit) {
        await showStepBanner(session.page, `【招商账号】查看分配商品并补充推广资料\n商品：${productId}\n状态：PENDING_AUDIT -> APPROVED`);
        const approved = await apiSuccess(
          api,
          'PUT',
          `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/audit-result`,
          session.auth,
          { data: buildAuditPayload(String(run.flow.runId)) }
        );
        approvedData = unwrap(approved.body) as JsonMap;
        assertTrue(
          String(approvedData.bizStatus) === 'APPROVED' || String(approvedData.bizStatus) === 'BOUND',
          `approved product should be business-ready, got ${approvedData.bizStatus}`
        );
        assertTrue(Boolean(approvedData.selectedToLibrary || approvedData.libraryVisible), 'approved product should enter library');
        run.flow.productAuditPerformed = true;
      } else {
        await showStepBanner(
          session.page,
          `【招商账号】复核已就绪商品资料\n商品：${productId}\n当前状态：${initialStatus}\n说明：当前 real-pre 活动暂无待审商品，本轮从已入库状态继续后续链路`
        );
        const existing = await apiSuccess(api, 'GET', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}`, session.auth);
        approvedData = unwrap(existing.body) as JsonMap;
        run.flow.productAuditPerformed = false;
      }
      const decision = await apiSuccess(
        api,
        'PUT',
        `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/decision`,
        session.auth,
        {
          data: {
            decisionLevel: 'MAIN',
            reason: `real-pre journey ${run.flow.runId} merchant decision`
          }
        }
      );
      const detail = await apiSuccess(api, 'GET', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}`, session.auth);
      const detailData = unwrap(detail.body) as JsonMap;
      run.flow.productLocalId = String(detailData.id || approvedData.id || '');
      run.flow.productTitle = String(detailData.title || run.flow.productTitle || productId);
      await navigate(session.page, '/product');
      await showStepBanner(
        session.page,
        requiresAudit
          ? `【招商账号】商品审核通过并入库\n商品：${productId}\n状态：${detailData.bizStatus}\n判定：selectedToLibrary=${Boolean(detailData.selectedToLibrary || detailData.libraryVisible)}`
          : `【招商账号】商品复核通过\n商品：${productId}\n状态保持：${initialStatus} -> ${detailData.bizStatus}\n判定：商品库可见，可继续转链/寄样`
      );
      const screenshot = await capture(run, session.page, 'merchant-product-library');
      return {
        productId,
        productLocalId: run.flow.productLocalId,
        initialStatus,
        auditPerformed: requiresAudit,
        bizStatus: detailData.bizStatus,
        selectedToLibrary: Boolean(detailData.selectedToLibrary || detailData.libraryVisible),
        decision: unwrap(decision.body) as JsonMap,
        screenshot
      };
    });

    const logout = await roleAction(run, 'merchant', '招商退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/products?page=1&size=1')
    );

    return { landing, audit, logout };
  } finally {
    finishRoleFlow(run, 'merchant');
    await session.context.close().catch(() => undefined);
  }
}

async function runChannel(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.channel, run);
  beginRoleFlow(run, 'channel', session.username);
  try {
    const landing = await roleAction(run, 'channel', '渠道登录、达人页和越权校验', async () => {
      await waitForIdle(session.page);
      await assertRouteRedirect(session.page, '/system/config');
      await navigate(session.page, '/talent?view=MY_TALENTS');
      const screenshot = await capture(run, session.page, 'channel-talent-home');
      return {
        currentPath: currentPath(session.page),
        screenshot
      };
    });

    const tempTalentCrud = await roleAction(run, 'channel', '渠道完成 QA 临时达人的增改删或等价收口', async () => {
      const tempUid = `qa_temp_${String(run.flow.runStamp).toLowerCase()}`;
      await showStepBanner(session.page, `【渠道账号】新增 QA 临时达人\n达人 UID：${tempUid}`);
      const create = await apiSuccess(api, 'POST', '/api/talents', session.auth, {
        data: {
          douyinUid: tempUid,
          douyinNo: tempUid,
          nickname: `qa_talent_${run.flow.runId}_temp`,
          contactPhone: '13000000001',
          intro: `temp talent for ${run.flow.runId}`
        }
      });
      const created = unwrap(create.body) as JsonMap;
      const talentId = String(created.id);
      run.flow.tempTalentUid = tempUid;
      run.flow.tempTalentId = talentId;

      const update = await apiSuccess(api, 'PUT', `/api/talents/${talentId}`, session.auth, {
        data: {
          nickname: `qa_talent_${run.flow.runId}_temp_updated`,
          contactPhone: '13000000002',
          intro: `temp talent updated ${run.flow.runId}`
        }
      });
      const updated = unwrap(update.body) as JsonMap;
      await apiSuccess(api, 'POST', `/api/talents/${talentId}/claims`, session.auth);
      await apiSuccess(api, 'POST', `/api/talents/${talentId}/release`, session.auth);
      const deleteAttempt = await rawApi(api, 'DELETE', `/api/talents/${talentId}`, session.auth);
      let retiredBy = 'delete';
      if (!isSuccess(deleteAttempt)) {
        retiredBy = 'release';
        run.warnings.push(`Temp talent ${tempUid} was released instead of deleted: HTTP ${deleteAttempt.status}`);
      }
      const listAfter = await apiSuccess(api, 'GET', '/api/talents', session.auth, {
        params: { page: 1, size: 20, keyword: tempUid }
      });
      if (retiredBy === 'delete') {
        assertTrue(
          !extractRecords(listAfter.body).some((item) => String(item.id) === talentId),
          'deleted temp talent should disappear from list'
        );
      }
      run.flow.tempTalentRetiredBy = retiredBy;
      await showStepBanner(
        session.page,
        `【渠道账号】临时达人 CRUD 判定通过\n新增 -> 修改 -> 认领 -> 释放${retiredBy === 'delete' ? ' -> 删除' : ''}`
      );
      const screenshot = await capture(run, session.page, 'channel-temp-talent-crud');
      return {
        talentId,
        tempUid,
        updatedContactPhone: updated.contactPhone,
        retiredBy,
        screenshot
      };
    });

    const promotionAndBusinessTalent = await roleAction(run, 'channel', '渠道创建业务达人并执行真实转链', async () => {
      const talentUid = `qa_journey_${String(run.flow.runStamp).toLowerCase()}`;
      const talentName = `qa_talent_${run.flow.runId}`;
      await showStepBanner(session.page, `【渠道账号】新增业务达人\n昵称：${talentName}\nUID：${talentUid}`);
      const create = await apiSuccess(api, 'POST', '/api/talents', session.auth, {
        data: {
          douyinUid: talentUid,
          douyinNo: talentUid,
          nickname: talentName,
          contactPhone: '13100000001',
          intro: `business talent for ${run.flow.runId}`
        }
      });
      const created = unwrap(create.body) as JsonMap;
      const talentId = String(created.id);
      await apiSuccess(api, 'PUT', `/api/talents/${talentId}`, session.auth, {
        data: {
          nickname: talentName,
          contactPhone: '13100000002',
          intro: `business talent updated ${run.flow.runId}`
        }
      });
      await apiSuccess(api, 'POST', `/api/talents/${talentId}/claims`, session.auth);

      const productId = requireFlowString(run, 'selectedProductId');
      await showStepBanner(session.page, `【渠道账号】执行真实转链\n商品：${productId}\n目标：生成 pick_source 并写入映射`);
      const promotion = await apiSuccess(
        api,
        'POST',
        `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/promotion-links`,
        session.auth,
        {
          data: {
            scene: 'PRODUCT_LIBRARY',
            needShortLink: true,
            externalUniqueId: `journey-${run.flow.runId}-${Date.now()}`
          }
        }
      );
      const promotionData = unwrap(promotion.body) as JsonMap;
      const pickSource = String(promotionData.pickSource || '');
      assertTrue(/^v\./.test(pickSource), `promotion should return real pick_source, got ${pickSource}`);
      const mappingCount = countPickSourceMapping(pickSource, productId, ACTIVITY_ID, session.auth.userId);
      assertTrue(mappingCount > 0, `pick_source_mapping should hit current channel, got ${mappingCount}`);

      run.flow.talentName = talentName;
      run.flow.talentUid = talentUid;
      run.flow.talentId = talentId;
      run.flow.pickSource = pickSource;
      run.flow.mappingCount = mappingCount;

      await navigate(session.page, `/talent?view=MY_TALENTS&keyword=${encodeURIComponent(talentName)}`);
      const body = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(body.includes(talentName), 'business talent should be visible in my talents page');
      await showStepBanner(
        session.page,
        `【渠道账号】转链与映射判定通过\npick_source：${pickSource}\npick_source_mapping：${mappingCount}`
      );
      const screenshot = await capture(run, session.page, 'channel-business-talent');
      return {
        talentId,
        talentUid,
        talentName,
        pickSource,
        mappingCount,
        screenshot
      };
    });

    const sampleSubmit = await roleAction(run, 'channel', '渠道通过寄样申请页提交样本并校验重复限制', async () => {
      const productLocalId = requireFlowString(run, 'productLocalId');
      const productId = requireFlowString(run, 'selectedProductId');
      const talentUid = requireFlowString(run, 'talentUid');
      const talentName = requireFlowString(run, 'talentName');
      const remark = `real-pre full journey ${run.flow.runId}`;

      await navigate(session.page, '/sample/apply');
      await visibleFill(session.page, session.page.getByPlaceholder('昵称/达人号'), talentUid, '【渠道账号】填写寄样达人 UID');
      await visibleClick(session.page, session.page.getByRole('button', { name: '搜索我的达人' }), '【渠道账号】点击搜索我的达人');
      await waitForIdle(session.page);
      const selectedTalentRow = await chooseFirstTalentRow(session.page);
      const selectedProductLabel = await selectSampleProduct(session.page, productId);

      await visibleFill(session.page, session.page.locator('textarea'), remark, '【渠道账号】填写寄样申请备注');
      const createResponsePromise = session.page.waitForResponse((response) => {
        const url = response.url();
        return url.includes('/api/samples') &&
          !url.includes('/eligibility-check') &&
          response.request().method() === 'POST';
      }, { timeout: 30_000 });
      await visibleClick(session.page, session.page.getByRole('button', { name: '提交申请' }), '【渠道账号】点击提交寄样申请');
      await visibleClick(session.page, session.page.getByRole('button', { name: '确认' }).last(), '【渠道账号】确认提交寄样申请');
      const createResponse = await createResponsePromise;
      await session.page.waitForURL('**/sample', { timeout: 30_000 });
      await waitForIdle(session.page);
      const createBody = await createResponse.json().catch(() => ({}));
      const sampleData = unwrap(createBody) as JsonMap;
      const sampleId = String(sampleData.id || '');
      const requestNo = String(sampleData.requestNo || '');
      assertTrue(Boolean(sampleId), 'sample create should return sample id');
      assertTrue(Boolean(requestNo), 'sample create should return requestNo');

      run.flow.sampleId = sampleId;
      run.flow.sampleRequestNo = requestNo;
      run.flow.sampleStatus = String(sampleData.status || 'PENDING_AUDIT');
      await showStepBanner(
        session.page,
        `【渠道账号】寄样申请创建成功\n状态：空 -> ${run.flow.sampleStatus}\n单号：${requestNo}`
      );

      const duplicate = await rawApi(api, 'POST', '/api/samples', session.auth, {
        data: {
          productId: productLocalId,
          talentId: talentUid,
          talentNickname: talentName,
          talentFansCount: 80000,
          talentCreditScore: 4.8,
          talentMainCategory: 'food',
          quantity: 1,
          remark
        }
      });
      assertTrue(!isSuccess(duplicate), 'duplicate sample request should be blocked by seven-day rule');
      await showStepBanner(
        session.page,
        `【渠道账号】重复寄样限制判定通过\n同达人 + 同商品 7 天内重复申请被拦截：HTTP ${duplicate.status}`
      );
      const body = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(body.includes(requestNo) || body.includes(talentName), 'sample page should reflect the newly created request');
      const screenshot = await capture(run, session.page, 'channel-sample-created');
      return {
        sampleId,
        requestNo,
        selectedTalentRow,
        selectedProductLabel,
        duplicateStatus: duplicate.status,
        duplicateCode: (duplicate.body as JsonMap | undefined)?.code,
        screenshot
      };
    });

    const logout = await roleAction(run, 'channel', '渠道退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/talents?page=1&size=1')
    );

    return { landing, tempTalentCrud, promotionAndBusinessTalent, sampleSubmit, logout };
  } finally {
    finishRoleFlow(run, 'channel');
    await session.context.close().catch(() => undefined);
  }
}

async function runMerchantSample(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.merchant, run);
  beginRoleFlow(run, 'merchant', session.username);
  try {
    const approve = await roleAction(run, 'merchant', '招商二次登录审核寄样申请', async () => {
      const sampleId = requireFlowString(run, 'sampleId');
      const requestNo = requireFlowString(run, 'sampleRequestNo');
      await navigate(session.page, `/sample/${sampleId}`);
      const bodyBefore = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(bodyBefore.includes(requestNo), 'merchant sample detail should show current request');
      await showStepBanner(session.page, `【招商账号】准备审核寄样\n单号：${requestNo}\n状态：PENDING_AUDIT -> PENDING_SHIP`);
      await visibleClick(session.page, session.page.getByRole('button', { name: '审核通过' }), '【招商账号】点击审核通过');
      await waitForIdle(session.page);
      const bodyAfter = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(bodyAfter.includes('待发货'), 'sample should move to pending shipment after approval');
      const sample = await apiSuccess(api, 'GET', `/api/samples/${sampleId}`, session.auth);
      const sampleData = unwrap(sample.body) as JsonMap;
      assertEqual(String(sampleData.status), 'PENDING_SHIP', 'sample status after merchant approval');
      run.flow.sampleStatus = sampleData.status;
      await showStepBanner(session.page, `【招商账号】寄样审核通过\n状态：PENDING_AUDIT -> ${sampleData.status}\n判定：详情页出现“待发货”且 API 状态一致`);
      const screenshot = await capture(run, session.page, 'merchant-sample-approved');
      return {
        requestNo,
        status: sampleData.status,
        screenshot
      };
    });

    const logout = await roleAction(run, 'merchant', '招商二次退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/samples?page=1&size=1&status=PENDING_AUDIT')
    );

    return { approve, logout };
  } finally {
    finishRoleFlow(run, 'merchant');
    await session.context.close().catch(() => undefined);
  }
}

async function runOperator(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.operator, run);
  beginRoleFlow(run, 'operator', session.username);
  try {
    const shipAndSign = await roleAction(run, 'operator', '运营录入物流并推进到待交作业', async () => {
      const sampleId = requireFlowString(run, 'sampleId');
      await navigate(session.page, '/ops/shipping');
      const boardScreenshot = await capture(run, session.page, 'operator-shipping-board');
      await navigate(session.page, `/sample/${sampleId}`);
      const trackingNo = `SF${String(run.flow.runStamp).replace(/_/g, '')}`;
      await showStepBanner(session.page, `【运营账号】准备录入物流\n状态：PENDING_SHIP -> SHIPPED\n物流单号：${trackingNo}`);
      await visibleClick(session.page, session.page.getByRole('button', { name: '发货' }), '【运营账号】点击发货');
      await visibleFill(session.page, session.page.getByPlaceholder('SF1234567890').last(), trackingNo, '【运营账号】填写物流单号');
      await visibleClick(session.page, session.page.getByRole('button', { name: '确认发货' }).last(), '【运营账号】确认发货');
      await waitForIdle(session.page);
      await expect(session.page.locator('body')).toContainText('快递中', { timeout: 20_000 });
      await showStepBanner(session.page, '【运营账号】发货成功\n状态：PENDING_SHIP -> SHIPPED\n页面显示：快递中');
      await visibleClick(session.page, session.page.getByRole('button', { name: '签收' }), '【运营账号】点击签收，推进到待交作业');
      await waitForIdle(session.page);
      await expect(session.page.locator('body')).toContainText('待交作业', { timeout: 20_000 });
      const sample = await apiSuccess(api, 'GET', `/api/samples/${sampleId}`, session.auth);
      const sampleData = unwrap(sample.body) as JsonMap;
      assertEqual(String(sampleData.status), 'PENDING_TASK', 'sample status after operator signoff');
      assertEqual(String(sampleData.trackingNo), trackingNo, 'tracking number should persist after shipment');
      run.flow.trackingNo = trackingNo;
      run.flow.sampleStatus = sampleData.status;
      await showStepBanner(session.page, `【运营账号】物流推进完成\n状态：SHIPPED -> ${sampleData.status}\n判定：页面显示“待交作业”且 API 物流单号一致`);
      const screenshot = await capture(run, session.page, 'operator-sample-pending-task');
      return {
        trackingNo,
        finalStatus: sampleData.status,
        boardScreenshot,
        screenshot
      };
    });

    const logout = await roleAction(run, 'operator', '运营退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/samples?page=1&size=1&status=PENDING_SHIP')
    );

    return { shipAndSign, logout };
  } finally {
    finishRoleFlow(run, 'operator');
    await session.context.close().catch(() => undefined);
  }
}

async function runChannelLeader(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.channelLeader, run);
  beginRoleFlow(run, 'channel-leader', session.username);
  try {
    const visibility = await roleAction(run, 'channel-leader', '渠道组长查看本组达人、寄样和权限边界', async () => {
      const requestNo = requireFlowString(run, 'sampleRequestNo');
      const talentName = requireFlowString(run, 'talentName');
      await showStepBanner(session.page, `【渠道组长】查看组内达人/寄样/业绩\n达人：${talentName}\n寄样单：${requestNo}`);
      await navigate(session.page, `/talent?view=TEAM_PRIVATE&keyword=${encodeURIComponent(talentName)}`);
      const talentBody = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(talentBody.includes('本组达人') || talentBody.includes(talentName), 'channel leader should open team talent view');
      const talentScreenshot = await capture(run, session.page, 'channel-leader-team-talents');
      await navigate(session.page, `/sample/${requireFlowString(run, 'sampleId')}`);
      const sampleBody = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(sampleBody.includes(requestNo), 'channel leader should see same-team sample detail');
      await assertRouteRedirect(session.page, '/system/config');
      const samples = await apiSuccess(api, 'GET', '/api/samples', session.auth, {
        params: { page: 1, size: 10, keyword: requestNo }
      });
      const talents = await apiSuccess(api, 'GET', '/api/talents', session.auth, {
        params: { page: 1, size: 20, keyword: talentName }
      });
      assertTrue(extractRecords(samples.body).some((item) => String(item.requestNo) === requestNo), 'team sample should be visible to channel leader');
      assertTrue(extractRecords(talents.body).some((item) => String(item.nickname || '') === talentName), 'team talent should be visible to channel leader');
      await showStepBanner(
        session.page,
        `【渠道组长】组内可见性判定通过\n达人记录：${extractRecords(talents.body).length}\n寄样记录：${extractRecords(samples.body).length}`
      );
      const screenshot = await capture(run, session.page, 'channel-leader-sample-detail');
      return {
        requestNo,
        talentName,
        teamSampleCount: extractRecords(samples.body).length,
        teamTalentCount: extractRecords(talents.body).length,
        talentScreenshot,
        screenshot
      };
    });

    const logout = await roleAction(run, 'channel-leader', '渠道组长退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/talents?page=1&size=1')
    );

    return { visibility, logout };
  } finally {
    finishRoleFlow(run, 'channel-leader');
    await session.context.close().catch(() => undefined);
  }
}

async function runAdminFinal(browser: Browser, api: APIRequestContext, run: RunState): Promise<JsonMap> {
  const session = await openRoleSession(browser, USERNAMES.admin, run);
  beginRoleFlow(run, 'cleanup', session.username);
  try {
    const review = await roleAction(run, 'cleanup', '管理员最终复核 Dashboard、订单和日志证据', async () => {
      const productId = requireFlowString(run, 'selectedProductId');
      const sampleId = requireFlowString(run, 'sampleId');
      await showStepBanner(session.page, `【管理员最终复核】Dashboard / 寄样 / 商品 / 日志\n商品：${productId}\n寄样：${sampleId}`);
      await navigate(session.page, '/dashboard');
      const dashboardScreenshot = await capture(run, session.page, 'cleanup-dashboard');
      await navigate(session.page, '/orders');
      const ordersScreenshot = await capture(run, session.page, 'cleanup-orders');
      await navigate(session.page, '/system/operation-logs');
      const operationLogScreenshot = await capture(run, session.page, 'cleanup-operation-logs');

      const dashboard = await apiSuccess(api, 'GET', '/api/dashboard/summary', session.auth);
      const productLogs = await apiSuccess(
        api,
        'GET',
        `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/operation-logs`,
        session.auth,
        { params: { page: 1, size: 50 } }
      );
      const sampleLogs = await apiSuccess(api, 'GET', `/api/samples/${sampleId}/status-logs`, session.auth);
      const systemLogs = await apiSuccess(api, 'GET', '/api/operation-logs', session.auth, {
        params: { page: 1, size: 20, username: USERNAMES.admin, module: '系统配置' }
      });
      const sampleLogRows = unwrap(sampleLogs.body) as JsonMap[];
      assertTrue(sampleLogRows.some((item) => String(item.toStatus) === 'PENDING_SHIP'), 'sample logs should contain merchant approval');
      assertTrue(sampleLogRows.some((item) => String(item.toStatus) === 'PENDING_TASK'), 'sample logs should contain operator handoff');
      await showStepBanner(
        session.page,
        `【管理员最终复核】证据判定通过\n样品日志包含：审核通过 + 运营签收\n商品日志：${extractRecords(productLogs.body).length}`
      );
      return {
        dashboard: summarizeResult(dashboard),
        productLogCount: extractRecords(productLogs.body).length,
        sampleLogCount: Array.isArray(sampleLogRows) ? sampleLogRows.length : 0,
        systemLogCount: extractRecords(systemLogs.body).length,
        dashboardScreenshot,
        ordersScreenshot,
        operationLogScreenshot
      };
    });

    const cleanup = await roleAction(run, 'cleanup', '管理员删除 QA 配置并校验安全边界', async () => {
      const configId = requireFlowString(run, 'configId');
      const configKey = requireFlowString(run, 'configKey');
      await showStepBanner(session.page, `【管理员最终复核】清理 QA 配置\n配置键：${configKey}`);
      await apiSuccess(api, 'DELETE', `/api/configs/${configId}`, session.auth);
      const pageAfterDelete = await apiSuccess(api, 'GET', '/api/configs', session.auth, {
        params: { page: 1, size: 10, keyword: configKey }
      });
      assertTrue(extractRecords(pageAfterDelete.body).length === 0, 'journey QA config should be removed after cleanup');
      await navigate(session.page, '/system/config');
      await pageSearchAndQuery(session.page, configKey);
      const pageText = await session.page.locator('body').innerText().catch(() => '');
      assertTrue(!pageText.includes(configKey), 'config page should not show deleted QA config');
      run.flow.configDeleted = true;
      run.warnings.push(
        `Run ${run.flow.runId}: product/sample artifacts remain tagged with runId because progressed sample records cannot be manually closed or deleted in current real-pre workflow.`
      );
      await showStepBanner(session.page, `【管理员最终复核】清理判定通过\n配置已删除：${configKey}`);
      const screenshot = await capture(run, session.page, 'cleanup-config-deleted');
      return {
        configKey,
        remainingConfigs: extractRecords(pageAfterDelete.body).length,
        screenshot
      };
    });

    const logout = await roleAction(run, 'cleanup', '管理员最终退出后旧令牌失效', async () =>
      logoutAndVerify(api, session.page, session.auth, '/api/users?page=1&size=1')
    );

    return { review, cleanup, logout };
  } finally {
    finishRoleFlow(run, 'cleanup');
    await session.context.close().catch(() => undefined);
  }
}

async function openRoleSession(browser: Browser, username: string, run: RunState): Promise<RoleSession> {
  const roleName = USER_ROLE_LABELS[username] || username;
  const context = await browser.newContext({
    baseURL: FRONTEND,
    viewport: VIEWPORT
  });
  const page = await context.newPage();
  await page.goto('/login', { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await visiblePause(page, `准备登录：${roleName}\n账号：${username}`);
  await visibleFill(page, page.locator('[data-testid="login-username"] input'), username, `填写登录账号：${roleName}`);
  await visibleFill(page, page.locator('[data-testid="login-password"] input'), DEFAULT_PASSWORD, `填写登录密码：${roleName}`, {
    displayValue: '演示账号密码'
  });
  const loginResponse = page.waitForResponse((response) =>
    response.url().includes('/api/auth/login') && response.request().method() === 'POST',
  { timeout: 30_000 });
  await visibleClick(page, page.locator('[data-testid="login-submit"]'), `点击登录：${roleName}`);
  const response = await loginResponse;
  assertTrue(response.ok(), `${username} login response should be OK`);
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
  await waitForIdle(page);
  const auth = await readAuthFromPage(page, username);
  await visiblePause(page, `登录成功：${roleName}\n当前账号：${auth.username}\n角色：${auth.roleCodes.join(', ')}`);
  await capture(run, page, `${roleSlug(username)}-login`);
  return { context, page, auth, username };
}

async function readAuthFromPage(page: Page, fallbackUsername: string): Promise<AuthState> {
  const payload = await page.evaluate(() => {
    const token = localStorage.getItem('token') || '';
    const refreshToken = localStorage.getItem('refreshToken') || '';
    const userInfoRaw = localStorage.getItem('userInfo') || '{}';
    return { token, refreshToken, userInfoRaw };
  });
  const user = JSON.parse(payload.userInfoRaw || '{}') as JsonMap;
  const token = String(payload.token || '');
  if (!token) {
    throw new Error(`login returned empty token for ${fallbackUsername}`);
  }
  return {
    token,
    refreshToken: String(payload.refreshToken || ''),
    user,
    userId: String(user.userId || user.id || ''),
    username: String(user.username || fallbackUsername),
    roleCodes: asStringArray(user.roleCodes),
    dataScope: user.dataScope,
    deptId: user.deptId == null ? null : String(user.deptId)
  };
}

async function waitForIdle(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function navigate(page: Page, route: string): Promise<void> {
  await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await waitForIdle(page);
  await showStepBanner(page, `打开页面：${route}`);
  await expect(page.locator('body')).not.toContainText(/Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i);
}

async function assertRouteRedirect(page: Page, route: string): Promise<void> {
  await showStepBanner(page, `验证权限边界：访问 ${route} 应被拦截或重定向`);
  await page.goto(route, { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await waitForIdle(page);
  const finalPath = currentPath(page);
  await showStepBanner(page, `权限边界通过：${route} -> ${finalPath}`);
  assertTrue(finalPath !== route, `route ${route} should redirect away, got ${finalPath}`);
}

async function capture(run: RunState, page: Page, name: string): Promise<string> {
  const safe = name.replace(/[^A-Za-z0-9_-]+/g, '_');
  run.screenshotSeq += 1;
  const file = join(run.screenshotsDir, `${pad(run.screenshotSeq)}-${safe}.png`);
  await page.screenshot({ path: file, fullPage: true }).catch(() => undefined);
  return file;
}

async function createAndUpdateJourneyConfig(page: Page, api: APIRequestContext, auth: AuthState, run: RunState): Promise<JsonMap> {
  const configKey = `journey.run_${String(run.flow.runStamp).toLowerCase()}.note`;
  const configValue = `created:${run.flow.runId}`;
  const configValueUpdated = `updated:${run.flow.runId}`;
  const configRemark = `real-pre full business journey ${run.flow.runId}`;

  run.flow.configKey = configKey;
  await navigate(page, '/system/config');
  const beforeScreenshot = await capture(run, page, 'admin-config-before-create');
  await visibleClick(page, page.getByRole('button', { name: '新增配置' }), '【管理员】点击新增配置');
  const modal = page.locator('.n-modal').last();
  await visibleFill(page, modal.getByPlaceholder('例：talent.protection_days'), configKey, '【管理员】填写配置键');
  await visibleFill(page, modal.getByPlaceholder('例：达人保护期天数'), `QA Journey ${run.flow.runId}`, '【管理员】填写配置名称');
  await visibleFill(page, modal.getByPlaceholder('配置值'), configValue, '【管理员】填写配置值');
  await openSelectAndChoose(modal.locator('.n-base-selection').first(), page, '寄样');
  await visibleFill(page, modal.getByPlaceholder('备注信息（可选）'), configRemark, '【管理员】填写配置备注');
  await visibleClick(page, modal.getByRole('button', { name: '确定' }), '【管理员】保存新增配置');
  await expect(modal).toBeHidden({ timeout: 20_000 });

  await pageSearchAndQuery(page, configKey);
  const createdText = await page.locator('body').innerText().catch(() => '');
  assertTrue(createdText.includes(configKey), 'created config should be visible in config page');

  const pageResult = await apiSuccess(api, 'GET', '/api/configs', auth, {
    params: { page: 1, size: 10, keyword: configKey }
  });
  const createdRow = extractRecords(pageResult.body)[0];
  assertTrue(Boolean(createdRow?.id), 'created config id should be queryable from API');
  run.flow.configId = String(createdRow.id);

  const row = page.locator('tbody tr').filter({ hasText: configKey }).first();
  await visibleClick(page, row.getByText('编辑'), '【管理员】点击编辑 QA 配置');
  const editModal = page.locator('.n-modal').last();
  await visibleFill(page, editModal.getByPlaceholder('配置值'), configValueUpdated, '【管理员】修改配置值');
  await visibleFill(page, editModal.getByPlaceholder('备注信息（可选）'), `${configRemark} updated`, '【管理员】修改备注');
  await visibleClick(page, editModal.getByRole('button', { name: '确定' }), '【管理员】保存修改后的配置');
  await expect(editModal).toBeHidden({ timeout: 20_000 });

  await pageSearchAndQuery(page, configKey);
  const updatedText = await page.locator('body').innerText().catch(() => '');
  assertTrue(updatedText.includes(configValueUpdated), 'updated config value should be visible in config page');

  const afterScreenshot = await capture(run, page, 'admin-config-after-update');
  run.flow.configValue = configValueUpdated;
  return {
    configKey,
    configId: run.flow.configId,
    beforeScreenshot,
    afterScreenshot
  };
}

async function openSelectAndChoose(trigger: ReturnType<Page['locator']>, page: Page, label: string): Promise<void> {
  await visibleClick(page, trigger, `选择下拉项：${label}`, { force: true });
  const option = page.locator('.n-base-select-option').filter({ hasText: label }).first();
  await option.waitFor({ state: 'visible', timeout: 10_000 });
  await visibleClick(page, option, `确认下拉项：${label}`, { force: true });
  await page.waitForTimeout(300);
}

async function pageSearchAndQuery(page: Page, keyword: string): Promise<void> {
  const input = page.locator('.config-toolbar input').last();
  await visibleFill(page, input, keyword, '填写查询关键字');
  await visibleClick(page, page.locator('.config-toolbar').getByRole('button', { name: '查询' }), '点击查询');
  await waitForIdle(page);
}

async function verifyDouyinOneClick(page: Page, run: RunState): Promise<JsonMap> {
  await navigate(page, '/system/douyin');
  await visibleClick(page, page.getByRole('button', { name: '一键刷新联调状态' }), '【管理员】点击一键刷新抖音联调状态', { timeout: 30_000 });
  const checked: string[] = [];
  for (const expected of DOUYIN_ONE_CLICK_CHECKS) {
    const locator = typeof expected === 'string'
      ? page.getByText(expected, { exact: true }).first()
      : page.getByText(expected).first();
    await expect(locator).toBeVisible({ timeout: 90_000 });
    checked.push((await locator.innerText().catch(() => '')).trim() || String(expected));
  }
  await showStepBanner(page, `【管理员】抖音配置刷新判定通过\n${checked.join('\n')}`);
  const screenshot = await capture(run, page, 'admin-douyin-one-click');
  return { checked, screenshot };
}

async function chooseFirstTalentRow(page: Page): Promise<string> {
  const button = page.getByRole('button', { name: '选择' }).first();
  await button.waitFor({ state: 'visible', timeout: 20_000 });
  const rowText = await button.locator('xpath=ancestor::tr').innerText().catch(() => '');
  await visibleClick(page, button, `【渠道账号】选择寄样达人\n${short(rowText, 120)}`, { force: true });
  await waitForIdle(page);
  return short(rowText, 180);
}

async function selectSampleProduct(page: Page, query: string): Promise<string> {
  const input = page.locator('.sample-apply .n-base-selection input').first();
  await visibleClick(page, input, '【渠道账号】打开寄样商品选择框', { force: true });
  await visibleFill(page, input, query, '【渠道账号】按商品 ID 搜索寄样商品');
  const option = page.locator('.n-base-select-option').first();
  await option.waitFor({ state: 'visible', timeout: 20_000 });
  const label = short(await option.innerText().catch(() => query), 200);
  await visibleClick(page, option, `【渠道账号】选择寄样商品\n${label}`, { force: true });
  await page.waitForTimeout(400);
  return label;
}

async function logoutAndVerify(
  api: APIRequestContext,
  page: Page,
  auth: AuthState,
  protectedApiPath: string
): Promise<JsonMap> {
  await showStepBanner(page, `${auth.username} 业务完成，准备退出\n退出后将验证旧 Token 访问 ${protectedApiPath} 被拒绝`);
  await visibleClick(page, page.locator('.user-info'), `${auth.username} 打开账号菜单`, { force: true }).catch(async () => {
    await visibleClick(page, page.locator('.user-name'), `${auth.username} 打开账号菜单`, { force: true });
  });
  await visibleClick(page, page.getByText('退出登录').first(), `${auth.username} 点击退出登录`, { force: true });
  await page.waitForURL('**/login', { timeout: 30_000 });
  const protectedResult = await rawApi(api, 'GET', protectedApiPath, auth);
  assertTrue(isForbidden(protectedResult), `old token should be rejected for ${protectedApiPath}`);
  await showStepBanner(page, `${auth.username} 已退出\n旧 Token 访问受保护接口返回：${protectedResult.status}`);
  return {
    redirectedTo: currentPath(page),
    protectedStatus: protectedResult.status,
    protectedCode: (protectedResult.body as JsonMap | undefined)?.code
  };
}

async function firstAssignableUser(api: APIRequestContext, auth: AuthState, keyword: string): Promise<JsonMap> {
  const result = await apiSuccess(api, 'GET', '/api/users/assignable', auth, {
    params: { keyword, page: 1, size: 20 }
  });
  const users = unwrap(result.body);
  if (!Array.isArray(users) || users.length === 0) {
    throw new Error(`assignable user not found: ${keyword}`);
  }
  const matched = users.find((item) => String((item as JsonMap).username || '').includes(keyword)) as JsonMap | undefined;
  return matched || users[0] as JsonMap;
}

function selectPendingAuditProduct(items: JsonMap[]): JsonMap {
  const preferred = items.find((item) =>
    String(item.productId ?? item.product_id) === PREFERRED_PRODUCT_ID &&
    String(item.bizStatus || 'PENDING_AUDIT') === 'PENDING_AUDIT'
  );
  if (preferred) return preferred;
  const pending = items.find((item) => String(item.bizStatus || 'PENDING_AUDIT') === 'PENDING_AUDIT' && (item.productId || item.product_id));
  if (pending) return pending;
  const reusableStatuses = new Set(['APPROVED', 'ASSIGNED', 'LINKED', 'FOLLOWING', 'BOUND']);
  const preferredReusable = items.find((item) =>
    String(item.productId ?? item.product_id) === PREFERRED_PRODUCT_ID &&
    reusableStatuses.has(String(item.bizStatus || ''))
  );
  if (preferredReusable) return preferredReusable;
  const reusable = items.find((item) => reusableStatuses.has(String(item.bizStatus || '')) && (item.productId || item.product_id));
  if (reusable) return reusable;
  const anyProduct = items.find((item) => item.productId || item.product_id);
  if (!anyProduct) {
    throw new Error('No usable product is available for journey handoff');
  }
  return anyProduct;
}

function buildAuditPayload(runId: string): JsonMap {
  return {
    approved: true,
    reason: `real-pre journey ${runId} product audit`,
    exclusivePriceRemark: `exclusive price verified for ${runId}`,
    shippingInfo: `shipping info verified for ${runId}`,
    sellingPoints: [
      `journey-${runId}-selling-point-a`,
      `journey-${runId}-selling-point-b`
    ],
    promotionScript: `promotion script for ${runId}`,
    supportsAds: true,
    rewardRemark: `reward remark for ${runId}`,
    participationRequirements: `participation requirements for ${runId}`,
    campaignTimeRemark: `campaign window for ${runId}`,
    materialFiles: [`journey-material-${runId}`],
    sampleThresholdSales: 30000,
    sampleThresholdLevel: 1,
    sampleThresholdRemark: `sample threshold for ${runId}`
  };
}

function currentPath(page: Page): string {
  try {
    return new URL(page.url()).pathname;
  } catch {
    return page.url();
  }
}

function roleSlug(username: string): string {
  return String(username || 'account').replace(/[^A-Za-z0-9_-]+/g, '-').replace(/_/g, '-');
}

async function apiSuccess(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  auth?: AuthState,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<RawApiResult> {
  const result = await rawApi(api, method, path, auth, options);
  if (!isSuccess(result)) {
    throw new Error(
      `${method} ${path} failed: HTTP ${result.status}, code=${(result.body as JsonMap | undefined)?.code}, msg=${
        (result.body as JsonMap | undefined)?.msg ||
        (result.body as JsonMap | undefined)?.message ||
        ''
      }`
    );
  }
  return result;
}

async function apiSuccessWithRetry(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  auth?: AuthState,
  options: { data?: JsonMap; params?: JsonMap } = {},
  retry: { attempts?: number; delayMs?: number } = {}
): Promise<RawApiResult> {
  const attempts = Math.max(1, retry.attempts ?? 3);
  const delayMs = Math.max(0, retry.delayMs ?? 2_000);
  let lastResult: RawApiResult | null = null;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const result = await rawApi(api, method, path, auth, options);
    if (isSuccess(result)) {
      return result;
    }
    lastResult = result;
    if (attempt < attempts && isRetryableUpstreamFailure(result)) {
      await sleep(delayMs * attempt);
      continue;
    }
    throw new Error(formatApiFailure(method, path, result));
  }
  throw new Error(formatApiFailure(method, path, lastResult));
}

async function rawApi(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  auth?: AuthState,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<RawApiResult> {
  const headers = auth ? { Authorization: `Bearer ${auth.token}` } : undefined;
  const started = Date.now();
  const requestOptions = {
    headers,
    data: options.data,
    params: options.params as Record<string, string | number | boolean> | undefined
  };
  const response = method === 'GET'
    ? await api.get(path, requestOptions)
    : method === 'POST'
      ? await api.post(path, requestOptions)
      : method === 'PUT'
        ? await api.put(path, requestOptions)
        : await api.delete(path, requestOptions);
  const body = await response.json().catch(async () => ({ rawText: await response.text().catch(() => '') }));
  return {
    status: response.status(),
    ok: response.ok(),
    body,
    durationMs: Date.now() - started
  };
}

function isSuccess(result: RawApiResult): boolean {
  const code = (result.body as JsonMap | undefined)?.code;
  return result.ok && (code === undefined || code === null || Number(code) === 200);
}

function isRetryableUpstreamFailure(result: RawApiResult): boolean {
  const body = result.body as JsonMap | undefined;
  const code = Number(body?.code);
  const message = String(body?.msg || body?.message || '');
  return code === 460 && /抖店服务异常|请稍后重试/.test(message);
}

function isForbidden(result: { status: number; body: unknown }): boolean {
  const code = Number((result.body as JsonMap | undefined)?.code);
  return [401, 403].includes(result.status) || [401, 403].includes(code);
}

function unwrap(body: unknown): unknown {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return (body as JsonMap).data;
  }
  return body;
}

function extractRecords(body: unknown): JsonMap[] {
  const data = unwrap(body);
  if (Array.isArray(data)) return data as JsonMap[];
  if (!data || typeof data !== 'object') return [];
  for (const key of ['records', 'items', 'list', 'rows', 'content', 'users', 'samples', 'activityList', 'productList']) {
    const value = (data as JsonMap)[key];
    if (Array.isArray(value)) return value as JsonMap[];
  }
  return [];
}

function summarizeResult(result: RawApiResult): JsonMap {
  const data = unwrap(result.body) as JsonMap | undefined;
  return {
    status: result.status,
    ok: result.ok,
    code: (result.body as JsonMap | undefined)?.code,
    durationMs: result.durationMs,
    total: data?.total ?? null,
    recordCount: extractRecords(result.body).length
  };
}

function formatApiFailure(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  result: RawApiResult | null
): string {
  return `${method} ${path} failed: HTTP ${result?.status ?? 'n/a'}, code=${(result?.body as JsonMap | undefined)?.code}, msg=${
    (result?.body as JsonMap | undefined)?.msg ||
    (result?.body as JsonMap | undefined)?.message ||
    ''
  }`;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function countPickSourceMapping(pickSource: string, productId: string, activityId: string, userId: string): number {
  const container = process.env.E2E_DB_CONTAINER || 'saas-postgres';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = [
    'select count(*)',
    'from pick_source_mapping',
    `where pick_source = ${sqlLiteral(pickSource)}`,
    `and product_id = ${sqlLiteral(productId)}`,
    `and activity_id = ${sqlLiteral(activityId)}`,
    `and user_id = ${sqlLiteral(userId)};`
  ].join(' ');
  const out = execFileSync('docker', ['exec', container, 'psql', '-U', user, '-d', db, '-tAc', sql], {
    encoding: 'utf8'
  }).trim();
  return Number.parseInt(out, 10) || 0;
}

function sqlLiteral(value: string): string {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function requireFlowString(run: RunState, key: string): string {
  const value = run.flow[key];
  if (!value) {
    throw new Error(`missing flow.${key}`);
  }
  return String(value);
}

function buildSummary(run: RunState): JsonMap {
  const conclusion = run.failures.length ? 'FAIL' : 'PASS';
  return {
    evidenceType: 'real-pre-full-business-journey-visual',
    conclusion,
    startedAt: run.startedAt,
    finishedAt: run.finishedAt,
    frontend: FRONTEND,
    backend: BACKEND,
    evidenceDir: run.evidenceDir,
    runId: run.flow.runId,
    execution: {
      headed: true,
      slowMoMs: Number(process.env.PW_SLOWMO_MS || 0),
      stepPauseMs: Number(process.env.PW_STEP_PAUSE_MS || 800),
      afterActionPauseMs: Number(process.env.PW_AFTER_ACTION_PAUSE_MS || 600),
      workers: 1,
      viewport: `${VIEWPORT.width}x${VIEWPORT.height}`,
      trace: 'on',
      video: 'on',
      screenshot: 'on'
    },
    roleSequence: ['管理员', '招商组长', '招商', '渠道', '招商二次登录', '运营', '渠道组长', '管理员最终复核'],
    coveredAccounts: ['管理员', '招商组长', '招商', '渠道', '招商二次登录', '运营', '渠道组长', '管理员最终复核'],
    coveredCapabilities: [
      '登录/退出',
      '菜单权限',
      '系统配置 CRUD',
      '活动商品同步',
      '商品分配',
      '商品审核',
      '达人 CRUD/等价动作',
      '真实转链',
      'pick_source_mapping',
      '寄样申请',
      '寄样审核',
      '物流录入',
      'Dashboard 复核',
      '越权拦截'
    ],
    coreJourney: [
      '管理员配置',
      '招商组长分配商品',
      '招商审核商品',
      '渠道创建达人/转链/申请寄样',
      '招商审核寄样',
      '运营发货签收',
      '渠道组长查看组内数据',
      '管理员复核与清理'
    ],
    overallPass: run.failures.length === 0,
    totalSteps: run.steps.length,
    passedSteps: run.steps.filter((step) => step.status === 'PASS').length,
    failedSteps: run.steps.filter((step) => step.status === 'FAIL').length,
    keyData: {
      runId: run.flow.runId,
      activityId: run.flow.activityId,
      productId: run.flow.selectedProductId,
      productLocalId: run.flow.productLocalId,
      pickSource: run.flow.pickSource,
      mappingCount: run.flow.mappingCount,
      talentName: run.flow.talentName,
      sampleRequestNo: run.flow.sampleRequestNo,
      finalSampleStatus: run.flow.sampleStatus,
      trackingNo: run.flow.trackingNo
    },
    failures: run.failures,
    warnings: run.warnings,
    flow: run.flow,
    steps: run.steps
  };
}

function flushEvidence(run: RunState): void {
  const summary = buildSummary(run);
  writeFileSync(join(run.evidenceDir, 'summary.json'), JSON.stringify(summary, null, 2), 'utf8');
  writeFileSync(join(run.evidenceDir, 'journey-state.json'), JSON.stringify(run.flow, null, 2), 'utf8');
  for (const [key, value] of Object.entries(run.roleFlows)) {
    writeFileSync(join(run.evidenceDir, `${key}-flow.json`), JSON.stringify(value, null, 2), 'utf8');
  }
  writeFileSync(join(run.evidenceDir, 'report.md'), buildReport(summary), 'utf8');
}

function buildReport(summary: JsonMap): string {
  const execution = summary.execution as JsonMap;
  const keyData = summary.keyData as JsonMap;
  const lines = [
    '# real-pre 浏览器可视化全业务剧本回归',
    '',
    `结论：${summary.conclusion}`,
    '',
    '## 执行方式',
    '',
    `- headed 浏览器：${boolText(Boolean(execution.headed))}`,
    `- slowMo：${execution.slowMoMs}ms`,
    `- step pause：${execution.stepPauseMs}ms`,
    `- after action pause：${execution.afterActionPauseMs}ms`,
    `- workers：${execution.workers}`,
    `- viewport：${execution.viewport}`,
    `- trace：${execution.trace}`,
    `- video：${execution.video}`,
    `- screenshot：${execution.screenshot}`,
    '',
    '## 角色顺序',
    '',
    '1. 管理员',
    '2. 招商组长',
    '3. 招商',
    '4. 渠道',
    '5. 招商二次登录',
    '6. 运营',
    '7. 渠道组长',
    '8. 管理员最终复核',
    '',
    '## 核心业务链路',
    '',
    '管理员配置',
    '-> 招商组长分配商品',
    '-> 招商审核商品',
    '-> 渠道新增达人/转链/申请寄样',
    '-> 招商审核寄样',
    '-> 运营发货',
    '-> 渠道组长查看组内数据',
    '-> 管理员复核全局数据',
    '',
    '## 关键数据',
    '',
    `- runId: ${keyData.runId || ''}`,
    `- activityId: ${keyData.activityId || ''}`,
    `- productId: ${keyData.productId || ''}`,
    `- productLocalId: ${keyData.productLocalId || ''}`,
    `- pickSource: ${keyData.pickSource || ''}`,
    `- mappingCount: ${keyData.mappingCount ?? ''}`,
    `- talentName: ${keyData.talentName || ''}`,
    `- sampleRequestNo: ${keyData.sampleRequestNo || ''}`,
    `- finalSampleStatus: ${keyData.finalSampleStatus || ''}`,
    `- trackingNo: ${keyData.trackingNo || ''}`,
    '',
    '## 结果',
    '',
    `- totalSteps: ${summary.totalSteps}`,
    `- passedSteps: ${summary.passedSteps}`,
    `- failedSteps: ${summary.failedSteps}`,
    `- overallPass: ${summary.overallPass}`,
    `- frontend: ${summary.frontend}`,
    `- backend: ${summary.backend}`,
    `- evidenceDir: ${summary.evidenceDir}`,
    '',
    '## 状态',
    ''
  ];

  const flow = summary.flow as JsonMap;
  for (const [key, value] of Object.entries(flow)) {
    lines.push(`- ${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`);
  }

  lines.push('', '## 步骤', '', '| Step | Result | Detail |', '| --- | --- | --- |');
  for (const step of summary.steps as StepResult[]) {
    lines.push(`| ${step.id} ${step.title} | ${step.status} | ${step.error ? step.error.replace(/\|/g, '/') : ''} |`);
  }

  const warnings = summary.warnings as string[];
  if (warnings.length) {
    lines.push('', '## Warnings', '');
    for (const warning of warnings) {
      lines.push(`- ${warning}`);
    }
  }

  const failures = summary.failures as string[];
  if (failures.length) {
    lines.push('', '## Failures', '');
    for (const failure of failures) {
      lines.push(`- ${failure}`);
    }
  }

  return `${lines.join('\n')}\n`;
}

function assertTrue(condition: boolean, message: string): void {
  if (!condition) {
    throw new Error(message);
  }
}

function assertEqual(actual: unknown, expected: unknown, label: string): void {
  if (actual !== expected) {
    throw new Error(`${label}: expected ${expected}, got ${actual}`);
  }
}

function asStringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((item) => String(item));
  if (value == null || value === '') return [];
  return String(value).split(',').map((item) => item.trim()).filter(Boolean);
}

function short(text: string, max = 200): string {
  const value = String(text || '').replace(/\s+/g, ' ').trim();
  return value.length > max ? `${value.slice(0, max)}...` : value;
}

function boolText(value: boolean): string {
  return value ? '是' : '否';
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function formatLocalTimestamp(date: Date): string {
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}
