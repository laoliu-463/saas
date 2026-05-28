import { expect, request as playwrightRequest, test, type APIRequestContext, type Browser, type BrowserContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

type JsonMap = Record<string, unknown>;
type ReusablePromotionMapping = {
  mappingId?: string;
  pickSource?: string;
  productId?: string;
  activityId?: string;
  userId?: string;
  promotionLinkId?: string;
  promotionUrl?: string;
  shortUrl?: string;
};

const {
  queryAnyReusablePromotionMapping,
  queryReusablePromotionMapping,
  selectReusablePromotionMapping,
  buildPromotionBlockerMessage
} = require('../../runtime/qa/real-pre-safe-upstream.cjs') as {
  queryAnyReusablePromotionMapping: (options?: JsonMap) => ReusablePromotionMapping[];
  queryReusablePromotionMapping: (options: JsonMap) => ReusablePromotionMapping[];
  selectReusablePromotionMapping: (rows: ReusablePromotionMapping[]) => ReusablePromotionMapping;
  buildPromotionBlockerMessage: (options: JsonMap) => string;
};

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

interface RoleCase {
  label: string;
  username: string;
  roleCode: string;
  expectedDataScope: number;
  allowedPages: string[];
  forbiddenPages: string[];
  expectedMenuText: string[];
  forbiddenMenuText: string[];
  allowedApi: string;
  protectedApiAfterLogout: string;
}

interface StepResult {
  id: string;
  title: string;
  status: 'PASS' | 'FAIL' | 'SKIP';
  details?: JsonMap;
  error?: string;
}

interface RunState {
  startedAt: string;
  finishedAt?: string;
  evidenceDir: string;
  screenshotsDir: string;
  accounts: Record<string, AuthState>;
  steps: StepResult[];
  failures: string[];
  flow: JsonMap;
}

const BACKEND = (process.env.E2E_BACKEND_URL || process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
const FRONTEND = (process.env.E2E_BASE_URL || process.env.FRONTEND_URL || 'http://localhost:3000').replace(/\/$/, '');
const API_BASE = `${BACKEND}/api`;
const ACTIVITY_ID = process.env.E2E_ROLE_ACTIVITY_ID || '3916506';
const SHOULD_RUN =
  process.env.E2E_REAL_PRE_ROLES === 'true' ||
  process.env.npm_lifecycle_event === 'e2e:real-pre:roles' ||
  process.argv.some((arg) => arg.includes('11-real-pre-role-business-flow'));
const EVIDENCE_DIR = process.env.E2E_ROLE_EVIDENCE_DIR || join(
  process.cwd(),
  'runtime',
  'qa',
  'out',
  `real-pre-role-business-e2e-${formatLocalTimestamp(new Date())}`
);
const DEFAULT_PASSWORD = 'admin123';
const REAL_PRE_NAV_TIMEOUT_MS = Number(process.env.E2E_REAL_PRE_NAV_TIMEOUT_MS || 120_000);
const REAL_PRE_UI_TIMEOUT_MS = Number(process.env.E2E_REAL_PRE_UI_TIMEOUT_MS || 120_000);
const REAL_PRE_NETWORK_IDLE_TIMEOUT_MS = Number(process.env.E2E_REAL_PRE_NETWORK_IDLE_TIMEOUT_MS || 5_000);
const DOUYIN_ONE_CLICK_REQUIRED_CHECKS = [
  'Token 正常',
  '授权主体正常',
  '活动商品已刷新',
  '商品 SKU 已验证',
  '订单同步成功',
] as const;
const DOUYIN_ONE_CLICK_DASHBOARD_CHECKS = [
  'Dashboard 已读取真实订单',
  'Dashboard 当前无订单'
] as const;

const ROLE_CASES: RoleCase[] = [
  {
    label: 'admin',
    username: 'admin',
    roleCode: 'admin',
    expectedDataScope: 3,
    allowedPages: ['/system/users', '/system/roles', '/system/config', '/system/douyin', '/dashboard', '/data', '/product', '/sample'],
    forbiddenPages: [],
    expectedMenuText: ['系统管理', '用户管理', '角色管理', '高级配置', '抖店联调', '数据看板', '商品库', '合作管理', '合作单'],
    forbiddenMenuText: [],
    allowedApi: '/users?page=1&size=5',
    protectedApiAfterLogout: '/users?page=1&size=1'
  },
  {
    label: 'biz_leader',
    username: 'biz_leader',
    roleCode: 'biz_leader',
    expectedDataScope: 2,
    allowedPages: ['/product/manage', '/product/manage/products', '/product', '/sample', '/data', '/dashboard'],
    forbiddenPages: ['/system/users', '/system/douyin'],
    expectedMenuText: ['商品管理', '活动列表', '商品库', '合作管理', '合作单', '数据'],
    forbiddenMenuText: ['系统管理', '抖店联调'],
    allowedApi: '/colonel/activities?page=1&size=5',
    protectedApiAfterLogout: '/colonel/activities?page=1&size=1'
  },
  {
    label: 'merchant',
    username: 'biz_staff',
    roleCode: 'biz_staff',
    expectedDataScope: 1,
    allowedPages: ['/product/manage/products', '/product', '/sample', '/data'],
    forbiddenPages: ['/system/users', '/talent'],
    expectedMenuText: ['商品列表', '商品库', '合作管理', '合作单', '我的业绩'],
    forbiddenMenuText: ['系统管理', '达人 CRM'],
    allowedApi: '/products?page=1&size=5',
    protectedApiAfterLogout: '/products?page=1&size=1'
  },
  {
    label: 'channel',
    username: 'channel_staff',
    roleCode: 'channel_staff',
    expectedDataScope: 1,
    allowedPages: ['/product', '/talent', '/sample', '/data'],
    forbiddenPages: ['/system/users', '/product/manage/products'],
    expectedMenuText: ['商品库', '我的达人', '公海达人', '合作管理', '合作单', '我的业绩'],
    forbiddenMenuText: ['系统管理', '商品管理', '活动列表'],
    allowedApi: '/talents?page=1&size=5',
    protectedApiAfterLogout: '/talents?page=1&size=1'
  },
  {
    label: 'operator',
    username: 'ops_staff',
    roleCode: 'ops_staff',
    expectedDataScope: 3,
    allowedPages: ['/ops/shipping'],
    forbiddenPages: ['/system/users', '/product', '/sample'],
    expectedMenuText: ['合作管理', '发货台'],
    forbiddenMenuText: ['系统管理', '商品库', '合作单', '达人 CRM'],
    allowedApi: '/samples?page=1&size=5&status=PENDING_SHIP',
    protectedApiAfterLogout: '/samples?page=1&size=1&status=PENDING_SHIP'
  },
  {
    label: 'channel_leader',
    username: 'channel_leader',
    roleCode: 'channel_leader',
    expectedDataScope: 2,
    allowedPages: ['/talent', '/sample', '/product', '/data', '/dashboard'],
    forbiddenPages: ['/system/users', '/system/douyin'],
    expectedMenuText: ['达人 CRM', '团队公海', '我的达人', '商品库', '合作管理', '合作单', '数据'],
    forbiddenMenuText: ['系统管理', '抖店联调'],
    allowedApi: '/talents?page=1&size=5',
    protectedApiAfterLogout: '/talents?page=1&size=1'
  }
];

test.describe.configure({ mode: 'serial' });

test('P3-5 real-pre role business flow validates menus, permissions, and handoffs', async ({ browser }, testInfo) => {
  test.skip(!SHOULD_RUN, 'Run with npm run e2e:real-pre:roles or set E2E_REAL_PRE_ROLES=true.');
  test.setTimeout(Number(process.env.E2E_REAL_PRE_ROLES_TIMEOUT_MS || 20 * 60_000));

  const run = createRunState();
  const api = await playwrightRequest.newContext({ baseURL: BACKEND, ignoreHTTPSErrors: true });

  try {
    await record(run, '00-env', 'real-pre environment guard', async () => {
      const adminAuth = await login(api, 'admin');
      const env = await apiSuccess(api, 'GET', '/api/system/env', adminAuth);
      const health = await rawApi(api, 'GET', '/api/system/health');
      const envData = unwrap(env.body) as JsonMap;
      assertTrue(
        String(envData.environmentLabel || '').toUpperCase() === 'REAL-PRE' ||
          asStringArray(envData.activeProfiles).includes('real-pre'),
        `Expected REAL-PRE environment, got ${JSON.stringify(envData)}`
      );
      assertTrue(envData.appTestEnabled === false, 'APP_TEST_ENABLED must be false');
      assertTrue(envData.douyinTestEnabled === false, 'DOUYIN_TEST_ENABLED must be false');
      assertTrue(health.ok && (health.body as JsonMap | undefined)?.status === 'UP', 'system health must be UP');
      return { environment: envData, health: health.body as JsonMap };
    }, undefined, true);

    await record(run, '01-login-all', 'all six role accounts login and expose expected user context', async () => {
      for (const roleCase of ROLE_CASES) {
        const auth = await login(api, roleCase.username);
        run.accounts[roleCase.label] = auth;
        assertTrue(auth.roleCodes.includes(roleCase.roleCode), `${roleCase.username} should include ${roleCase.roleCode}`);
        assertEqual(Number(auth.dataScope), roleCase.expectedDataScope, `${roleCase.username} dataScope`);
      }
      return Object.fromEntries(Object.entries(run.accounts).map(([key, auth]) => [
        key,
        {
          username: auth.username,
          roleCodes: auth.roleCodes,
          dataScope: auth.dataScope,
          deptId: auth.deptId,
          userId: auth.userId
        }
      ]));
    }, undefined, true);

    await record(run, '02-token-preflight', 'real-pre token cache is available before upstream checks', async () => {
      return assertDouyinTokenReady(api, requireAuth(run, 'admin'));
    }, undefined, true);

    await record(run, '03-admin', 'admin validates system, global APIs, and admin-only surfaces', async () => {
      const auth = requireAuth(run, 'admin');
      const ui = await verifyRoleUi(browser, auth, ROLE_CASES[0]);
      const douyinRefresh = await verifyDouyinOneClick(browser, auth);
      const apiChecks = await verifyAllowedApis(api, auth, [
        '/users?page=1&size=5',
        '/roles?page=1&size=5',
        '/roles/enabled',
        '/configs/grouped',
        '/douyin/tokens',
        '/dashboard/summary',
        '/products?page=1&size=5',
        '/samples?page=1&size=5',
        '/operation-logs?page=1&size=5'
      ]);
      return { ui, douyinRefresh, apiChecks };
    });

    await record(run, '03-biz-leader', 'biz_leader syncs activity products and assigns audit owner', async () => {
      const auth = requireAuth(run, 'biz_leader');
      const ui = await verifyRoleUi(browser, auth, ROLE_CASES[1]);
      const activities = await apiSuccess(api, 'GET', '/api/colonel/activities', auth, { params: { page: 1, size: 10 } });
      const productList = await apiSuccess(api, 'GET', `/api/colonel/activities/${ACTIVITY_ID}/products`, auth, {
        params: { count: 20, refresh: true }
      });
      const products = extractRecords(productList.body);
      assertTrue(products.length > 0, 'activity product sync should return products');
      const auditProduct = findProductCandidate(products, 'PENDING_AUDIT');
      const rejectProduct = products.find((item) =>
        String(item.productId ?? item.product_id) !== String(auditProduct.productId ?? auditProduct.product_id) &&
        String(item.bizStatus || 'PENDING_AUDIT') === 'PENDING_AUDIT'
      ) as JsonMap | undefined;
      assertTrue(Boolean(rejectProduct), 'need a second PENDING_AUDIT product for reject-path validation');
      const bizStaff = await firstAssignableUser(api, auth, 'biz_staff');
      const productId = String(auditProduct.productId ?? auditProduct.product_id);
      const rejectProductId = String(rejectProduct?.productId ?? rejectProduct?.product_id);
      run.flow.productOperationStateSnapshots = [
        readProductOperationStateSnapshot(ACTIVITY_ID, productId),
        readProductOperationStateSnapshot(ACTIVITY_ID, rejectProductId)
      ];
      const productLogIdsBefore = [
        ...readProductOperationLogIds(ACTIVITY_ID, productId),
        ...readProductOperationLogIds(ACTIVITY_ID, rejectProductId)
      ];
      await apiSuccess(api, 'PUT', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/audit-assignee`, auth, {
        data: { assigneeId: bizStaff.id }
      });
      await apiSuccess(api, 'PUT', `/api/colonel/activities/${ACTIVITY_ID}/products/${rejectProductId}/audit-assignee`, auth, {
        data: { assigneeId: bizStaff.id }
      });
      await expectForbidden(api, auth, 'GET', '/api/users?page=1&size=5');
      await expectForbiddenRoute(browser, auth, '/system/douyin');
      run.flow.productId = productId;
      run.flow.rejectProductId = rejectProductId;
      run.flow.bizStaffUserId = bizStaff.id;
      run.flow.productOperationLogIds = diffNewIds(productLogIdsBefore, [
        ...readProductOperationLogIds(ACTIVITY_ID, productId),
        ...readProductOperationLogIds(ACTIVITY_ID, rejectProductId)
      ]);
      return {
        ui,
        activities: summarizeResult(activities),
        syncedProductCount: products.length,
        productId,
        rejectProductId,
        assignedTo: bizStaff.username
      };
    });

    await record(run, '04-merchant', 'merchant audits one product through and rejects another product', async () => {
      const auth = requireAuth(run, 'merchant');
      const ui = await verifyRoleUi(browser, auth, ROLE_CASES[2]);
      const productId = requireFlowString(run, 'productId');
      const rejectProductId = requireFlowString(run, 'rejectProductId');
      const runId = requireFlowString(run, 'runId');
      const productLogIdsBefore = [
        ...readProductOperationLogIds(ACTIVITY_ID, productId),
        ...readProductOperationLogIds(ACTIVITY_ID, rejectProductId)
      ];
      const approved = await apiSuccess(api, 'PUT', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/audit-result`, auth, {
        data: buildAuditPayload(`${runId}-approved`)
      });
      const approvedData = unwrap(approved.body) as JsonMap;
      assertTrue(String(approvedData.bizStatus) === 'APPROVED', `approved product should be APPROVED, got ${approvedData.bizStatus}`);
      assertTrue(Boolean(approvedData.selectedToLibrary || approvedData.libraryVisible), 'approved product should enter library');
      const rejected = await apiSuccess(api, 'PUT', `/api/colonel/activities/${ACTIVITY_ID}/products/${rejectProductId}/audit-result`, auth, {
        data: { approved: false, reason: `P3-5 role smoke reject path ${runId}` }
      });
      const rejectedData = unwrap(rejected.body) as JsonMap;
      assertTrue(String(rejectedData.bizStatus) === 'REJECTED', `rejected product should be REJECTED, got ${rejectedData.bizStatus}`);
      const picks = await apiSuccess(api, 'GET', '/api/products', auth, { params: { page: 1, size: 10 } });
      await expectForbidden(api, auth, 'GET', '/api/users?page=1&size=5');
      await expectForbidden(api, auth, 'POST', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/promotion-links`, { data: { scene: 'PRODUCT_LIBRARY', externalUniqueId: runId } });
      run.flow.productOperationLogIds = mergeUniqueStringArrays(
        asStringArray(run.flow.productOperationLogIds),
        diffNewIds(productLogIdsBefore, [
          ...readProductOperationLogIds(ACTIVITY_ID, productId),
          ...readProductOperationLogIds(ACTIVITY_ID, rejectProductId)
        ])
      );
      run.flow.productLocalId = String(approvedData.id);
      return {
        ui,
        productId,
        productLocalId: run.flow.productLocalId,
        approvedStatus: approvedData.bizStatus,
        rejectedStatus: rejectedData.bizStatus,
        productList: summarizeResult(picks)
      };
    });

    await record(run, '05-channel', 'channel reuses promotion mapping, creates talent claim, and submits sample request', async () => {
      const auth = requireAuth(run, 'channel');
      const ui = await verifyRoleUi(browser, auth, ROLE_CASES[3]);
      const productId = requireFlowString(run, 'productId');
      const productLocalId = requireFlowString(run, 'productLocalId');
      const library = await apiSuccess(api, 'GET', '/api/products', auth, { params: { page: 1, size: 10 } });
      let reusableMappings = queryReusablePromotionMapping({
        activityId: ACTIVITY_ID,
        productId,
        userId: auth.userId
      });
      let reusableMapping: ReusablePromotionMapping;
      let mappingScope = 'current-channel-product';
      try {
        reusableMapping = selectReusablePromotionMapping(reusableMappings);
      } catch {
        reusableMappings = queryAnyReusablePromotionMapping({ limit: 5 });
        mappingScope = 'global-reusable';
        try {
          reusableMapping = selectReusablePromotionMapping(reusableMappings);
        } catch {
          throw new Error(buildPromotionBlockerMessage({ activityId: ACTIVITY_ID, productId, userId: auth.userId }));
        }
      }
      const pickSource = String(reusableMapping.pickSource || '');
      assertTrue(/^v\./.test(pickSource), `reusable mapping should expose real pick_source, got ${pickSource}`);
      const mappingCount = reusableMappings.length;
      assertTrue(mappingCount > 0, `reusable pick_source_mapping should be available, got ${mappingCount}`);
      const sampleActivityId = String(reusableMapping.activityId || ACTIVITY_ID);
      const sampleProductId = String(reusableMapping.productId || productId);
      let sampleProductLocalId = productLocalId;
      if (mappingScope !== 'current-channel-product' || sampleActivityId !== ACTIVITY_ID || sampleProductId !== productId) {
        const adminAuth = requireAuth(run, 'admin');
        const sampleProductDetail = await apiSuccess(
          api,
          'GET',
          `/api/colonel/activities/${sampleActivityId}/products/${sampleProductId}`,
          adminAuth
        );
        const sampleProductData = unwrap(sampleProductDetail.body) as JsonMap;
        assertTrue(Boolean(sampleProductData.id), `mapped reusable product should resolve to local product id: activityId=${sampleActivityId}, productId=${sampleProductId}`);
        sampleProductLocalId = String(sampleProductData.id);
      }

      const runId = requireFlowString(run, 'runId');
      const talentUid = `${runId}_p35_${Date.now()}`;
      const talentCreate = await apiSuccess(api, 'POST', '/api/talents', auth, {
        data: {
          douyinUid: talentUid,
          douyinNo: talentUid,
          nickname: `P3-5 role talent ${runId}`,
          fansCount: 80000,
          level: 'L5',
          contactWechat: `wx_${talentUid}`,
          ipLocation: 'Shanghai'
        }
      });
      const talent = unwrap(talentCreate.body) as JsonMap;
      const talentId = String(talent.id);
      await apiSuccess(api, 'POST', `/api/talents/${talentId}/claims`, auth);
      const privateTalents = await apiSuccess(api, 'GET', '/api/talents/pools/private', auth);
      const sampleBody = {
        productId: sampleProductLocalId,
        talentId: talentUid,
        talentNickname: `P3-5 role talent ${runId}`,
        talentFansCount: 80000,
        talentCreditScore: 4.8,
        talentMainCategory: 'food',
        quantity: 1,
        remark: `P3-5 real-pre role flow ${runId}`
      };
      const sample = await apiSuccess(api, 'POST', '/api/samples', auth, { data: sampleBody });
      const sampleData = unwrap(sample.body) as JsonMap;
      const duplicate = await rawApi(api, 'POST', '/api/samples', auth, { data: sampleBody });
      assertTrue(!isSuccess(duplicate), 'duplicate sample request should be blocked by seven-day rule');
      await expectForbidden(api, auth, 'GET', '/api/users?page=1&size=5');
      await expectForbidden(api, auth, 'PUT', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/audit-result`, { data: buildAuditPayload('channel-forbidden') });
      run.flow.pickSource = pickSource;
      run.flow.mappingId = reusableMapping.mappingId || '';
      run.flow.promotionLinkId = reusableMapping.promotionLinkId || '';
      run.flow.promotionUrl = reusableMapping.promotionUrl || '';
      run.flow.mappingCount = mappingCount;
      run.flow.mappingScope = mappingScope;
      run.flow.sampleProductId = sampleProductId;
      run.flow.sampleProductLocalId = sampleProductLocalId;
      run.flow.talentId = talentId;
      run.flow.talentUid = talentUid;
      run.flow.sampleId = String(sampleData.id);
      run.flow.sampleRequestNo = String(sampleData.requestNo);
      return {
        ui,
        library: summarizeResult(library),
        safeUpstreamMode: run.flow.safeUpstreamMode,
        mappingScope,
        pickSource,
        mappingId: run.flow.mappingId,
        promotionLinkId: run.flow.promotionLinkId,
        mappingCount,
        sampleProductId,
        sampleProductLocalId,
        talentId,
        privateTalentCount: extractRecords(privateTalents.body).length,
        sampleId: run.flow.sampleId,
        sampleRequestNo: run.flow.sampleRequestNo,
        duplicateStatus: duplicate.status,
        duplicateCode: (duplicate.body as JsonMap | undefined)?.code
      };
    });

    await record(run, '06-merchant-sample-audit', 'merchant audits the channel sample request into pending shipment', async () => {
      const auth = requireAuth(run, 'merchant');
      const sampleId = requireFlowString(run, 'sampleId');
      const runId = requireFlowString(run, 'runId');
      const sample = await apiSuccess(api, 'PUT', `/api/samples/${sampleId}/status`, auth, {
        data: { action: 'PENDING_SHIP', reason: `P3-5 merchant sample approval ${runId}` }
      });
      const sampleData = unwrap(sample.body) as JsonMap;
      assertEqual(String(sampleData.status), 'PENDING_SHIP', 'sample status after merchant audit');
      const logs = await apiSuccess(api, 'GET', `/api/samples/${sampleId}/status-logs`, auth);
      run.flow.sampleStatusAfterMerchant = sampleData.status;
      return { sample: summarizeResult(sample), statusLogs: summarizeResult(logs) };
    });

    await record(run, '07-operator', 'operator ships sample and advances it to pending homework', async () => {
      const auth = requireAuth(run, 'operator');
      const ui = await verifyRoleUi(browser, auth, ROLE_CASES[4]);
      const sampleId = requireFlowString(run, 'sampleId');
      const requestNo = requireFlowString(run, 'sampleRequestNo');
      const runId = requireFlowString(run, 'runId');
      const pending = await apiSuccess(api, 'GET', '/api/samples', auth, {
        params: { page: 1, size: 10, status: 'PENDING_SHIP', keyword: requestNo }
      });
      assertTrue(extractRecords(pending.body).some((item) => String(item.id) === sampleId), 'operator should see pending shipment sample');
      const trackingNo = `SF${runId}_${Date.now()}`;
      const shipped = await apiSuccess(api, 'PUT', `/api/samples/${sampleId}/status`, auth, {
        data: { action: 'SHIPPING', trackingNo, shipperCode: 'SF', reason: `P3-5 operator shipment ${runId}` }
      });
      const shippedData = unwrap(shipped.body) as JsonMap;
      assertEqual(String(shippedData.status), 'SHIPPED', 'sample status after operator shipment');
      assertEqual(String(shippedData.trackingNo), trackingNo, 'tracking number should persist');
      const pendingHomework = await apiSuccess(api, 'PUT', `/api/samples/${sampleId}/status`, auth, {
        data: { action: 'PENDING_HOMEWORK', reason: `P3-5 operator manual delivery handoff ${runId}` }
      });
      const pendingHomeworkData = unwrap(pendingHomework.body) as JsonMap;
      assertEqual(String(pendingHomeworkData.status), 'PENDING_TASK', 'sample status after operator handoff');
      await expectForbidden(api, auth, 'GET', '/api/products?page=1&size=5');
      await expectForbidden(api, auth, 'PUT', `/api/colonel/activities/${ACTIVITY_ID}/products/${requireFlowString(run, 'productId')}/audit-result`, {
        data: buildAuditPayload('operator-forbidden')
      });
      run.flow.trackingNo = trackingNo;
      run.flow.sampleFinalStatus = pendingHomeworkData.status;
      return { ui, pending: summarizeResult(pending), trackingNo, finalStatus: pendingHomeworkData.status };
    });

    await record(run, '08-channel-leader', 'channel_leader validates team data range and dashboard access', async () => {
      const auth = requireAuth(run, 'channel_leader');
      const ui = await verifyRoleUi(browser, auth, ROLE_CASES[5]);
      const requestNo = requireFlowString(run, 'sampleRequestNo');
      const samples = await apiSuccess(api, 'GET', '/api/samples', auth, {
        params: { page: 1, size: 10, keyword: requestNo }
      });
      assertTrue(extractRecords(samples.body).some((item) => String(item.requestNo) === requestNo), 'channel_leader should see same-team sample');
      const dashboard = await apiSuccess(api, 'GET', '/api/dashboard/summary', auth);
      const talents = await apiSuccess(api, 'GET', '/api/talents', auth, { params: { page: 1, size: 10 } });
      await expectForbidden(api, auth, 'GET', '/api/users?page=1&size=5');
      await expectForbidden(api, auth, 'POST', `/api/talents/${requireFlowString(run, 'talentId')}/override-assignee`, {
        data: { newUserId: requireAuth(run, 'channel').userId, reason: `channel leader should not override assignment ${requireFlowString(run, 'runId')}` }
      });
      return { ui, samples: summarizeResult(samples), dashboard: summarizeResult(dashboard), talents: summarizeResult(talents) };
    });

    await record(run, '09-admin-review', 'admin reviews product logs, sample logs, order sync, and dashboard', async () => {
      const auth = requireAuth(run, 'admin');
      const productId = requireFlowString(run, 'productId');
      const sampleId = requireFlowString(run, 'sampleId');
      const productLogs = await apiSuccess(api, 'GET', `/api/colonel/activities/${ACTIVITY_ID}/products/${productId}/operation-logs`, auth, {
        params: { page: 1, size: 20 }
      });
      assertTrue(extractRecords(productLogs.body).length > 0, 'product operation logs should be traceable');
      const sampleLogs = await apiSuccess(api, 'GET', `/api/samples/${sampleId}/status-logs`, auth);
      assertTrue(extractRecords(sampleLogs.body).length > 0, 'sample status logs should be traceable');
      const end = new Date();
      const start = new Date(end.getTime() - 30 * 60 * 1000);
      const orderSync = await apiSuccess(api, 'POST', '/api/orders/sync', auth, {
        data: { startTime: formatLocalDateTime(start), endTime: formatLocalDateTime(end) }
      });
      const orderSyncData = unwrap(orderSync.body) as JsonMap;
      assertTrue(Number(orderSyncData.failed ?? orderSyncData.failedCount ?? 0) === 0, 'order sync failed count should be zero');
      const dashboard = await apiSuccess(api, 'GET', '/api/dashboard/summary', auth);
      const dashboardPage = await openPage(browser, auth, '/dashboard', true);
      run.flow.orderSync = {
        totalFetched: Number(orderSyncData.totalFetched ?? orderSyncData.fetched ?? orderSyncData.total ?? 0),
        created: Number(orderSyncData.created ?? 0),
        updated: Number(orderSyncData.updated ?? 0),
        attributed: Number(orderSyncData.attributed ?? 0),
        unattributed: Number(orderSyncData.unattributed ?? 0),
        failed: Number(orderSyncData.failed ?? orderSyncData.failedCount ?? 0)
      };
      return {
        productLogs: summarizeResult(productLogs),
        sampleLogs: summarizeResult(sampleLogs),
        orderSync: run.flow.orderSync,
        dashboard: summarizeResult(dashboard),
        dashboardPage
      };
    });

    await record(run, '10-logout-protection', 'all accounts revoke tokens and cannot keep accessing protected APIs', async () => {
      const results: JsonMap[] = [];
      for (const roleCase of ROLE_CASES) {
        const auth = await login(api, roleCase.username);
        const logout = await rawApi(api, 'POST', '/api/auth/logout', auth, {
          data: { accessToken: auth.token, refreshToken: auth.refreshToken || undefined }
        });
        assertTrue(isSuccess(logout), `${roleCase.username} logout should succeed`);
        const protectedResult = await rawApi(api, 'GET', `/api${roleCase.protectedApiAfterLogout}`, auth);
        assertTrue(isForbidden(protectedResult), `${roleCase.username} old token should be rejected`);
        results.push({
          username: roleCase.username,
          logoutStatus: logout.status,
          logoutCode: (logout.body as JsonMap | undefined)?.code,
          protectedStatus: protectedResult.status,
          protectedCode: (protectedResult.body as JsonMap | undefined)?.code
        });
      }
      return { results };
    });
  } finally {
    await api.dispose();
    run.finishedAt = new Date().toISOString();
    writeEvidence(run);
  }

  await testInfo.attach('p3-5-role-business-summary.json', {
    body: JSON.stringify(buildSummary(run), null, 2),
    contentType: 'application/json'
  });

  expect(run.failures, `P3-5 role business flow failures:\n${run.failures.join('\n')}`).toEqual([]);
});

function createRunState(): RunState {
  mkdirSync(EVIDENCE_DIR, { recursive: true });
  const screenshotsDir = join(EVIDENCE_DIR, 'screenshots');
  mkdirSync(screenshotsDir, { recursive: true });
  const runId = process.env.QA_RUN_ID || `QA${formatLocalTimestamp(new Date()).replace(/[^0-9]/g, '')}`;
  return {
    startedAt: new Date().toISOString(),
    evidenceDir: EVIDENCE_DIR,
    screenshotsDir,
    accounts: {},
    steps: [],
    failures: [],
    flow: {
      runId,
      activityId: ACTIVITY_ID,
      safeUpstreamMode: 'REUSE_EXISTING_PROMOTION_MAPPING',
      databaseCleared: false,
      cleanupStatus: 'PLAN_REQUIRED_BEFORE_COMPLETION',
      secretsWrittenToDocsOrGit: false
    }
  };
}

async function record(
  run: RunState,
  id: string,
  title: string,
  fn: () => Promise<JsonMap | void>,
  recover?: () => Promise<void>,
  failFast = false
): Promise<void> {
  try {
    const details = await fn();
    run.steps.push({ id, title, status: 'PASS', details: details || {} });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    run.failures.push(`${id} ${title}: ${message}`);
    run.steps.push({ id, title, status: 'FAIL', error: message });
    if (recover) {
      try {
        await recover();
      } catch (recoverError) {
        const recoverMessage = recoverError instanceof Error ? recoverError.message : String(recoverError);
        run.failures.push(`${id} recovery failed: ${recoverMessage}`);
      }
    }
    if (failFast) {
      throw error;
    }
  }
}

async function login(api: APIRequestContext, username: string): Promise<AuthState> {
  const result = await rawApi(api, 'POST', '/api/auth/login', undefined, {
    data: { username, password: DEFAULT_PASSWORD }
  });
  if (!isSuccess(result)) {
    throw new Error(`login failed for ${username}: HTTP ${result.status}, code=${(result.body as JsonMap | undefined)?.code}`);
  }
  const data = unwrap(result.body) as JsonMap;
  const token = String(data.token || '');
  if (!token) {
    throw new Error(`login returned empty token for ${username}`);
  }
  return {
    token,
    refreshToken: String(data.refreshToken || ''),
    user: data,
    userId: String(data.userId || data.id || ''),
    username: String(data.username || username),
    roleCodes: asStringArray(data.roleCodes),
    dataScope: data.dataScope,
    deptId: data.deptId == null ? null : String(data.deptId)
  };
}

async function assertDouyinTokenReady(api: APIRequestContext, auth: AuthState): Promise<JsonMap> {
  const result = await apiSuccess(api, 'GET', '/api/douyin/tokens', auth);
  const data = unwrap(result.body) as JsonMap;
  const ready =
    data.hasAccessToken === true &&
    data.hasRefreshToken === true &&
    data.reauthorizeRequired !== true;
  assertTrue(
    ready,
    `REAL_PRE_TOKEN_PRECONDITION_BLOCKED: hasAccessToken=${data.hasAccessToken}, hasRefreshToken=${data.hasRefreshToken}, reauthorizeRequired=${data.reauthorizeRequired}`
  );
  return {
    appId: data.appId,
    hasAccessToken: data.hasAccessToken,
    hasRefreshToken: data.hasRefreshToken,
    tokenExpiringSoon: data.tokenExpiringSoon,
    reauthorizeRequired: data.reauthorizeRequired
  };
}

async function verifyRoleUi(browser: Browser, auth: AuthState, roleCase: RoleCase): Promise<JsonMap> {
  const allowedPages = [];
  for (const route of roleCase.allowedPages) {
    allowedPages.push(await openPage(browser, auth, route, true));
  }
  const forbiddenPages = [];
  for (const route of roleCase.forbiddenPages) {
    forbiddenPages.push(await openPage(browser, auth, route, false));
  }
  const chromeText = allowedPages.map((item) => String(item.chromeText || '')).join('\n');
  const missingMenu = roleCase.expectedMenuText.filter((text) => !chromeText.includes(text));
  const leakedMenu = roleCase.forbiddenMenuText.filter((text) => chromeText.includes(text));
  assertTrue(missingMenu.length === 0, `${roleCase.label} missing menu text: ${missingMenu.join(', ')}`);
  assertTrue(leakedMenu.length === 0, `${roleCase.label} leaked forbidden menu text: ${leakedMenu.join(', ')}`);
  return { allowedPages, forbiddenPages, missingMenu, leakedMenu };
}

async function verifyDouyinOneClick(browser: Browser, auth: AuthState): Promise<JsonMap> {
  const context = await newAuthContext(browser, auth);
  const page = await context.newPage();
  const pageErrors: string[] = [];
  const badResponses: JsonMap[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('response', (response) => {
    const url = response.url();
    const status = response.status();
    if (url.includes('/api/') && ([401, 403].includes(status) || status >= 500)) {
      badResponses.push({ url: redactUrl(url), status });
    }
  });

  try {
    await page.goto('/system/douyin', { waitUntil: 'domcontentloaded', timeout: REAL_PRE_NAV_TIMEOUT_MS });
    await waitForAppReady(page);
    await page.getByRole('button', { name: '一键刷新联调状态' }).click({ timeout: REAL_PRE_UI_TIMEOUT_MS });

    const checked: string[] = [];
    for (const text of DOUYIN_ONE_CLICK_REQUIRED_CHECKS) {
      await expect(page.getByText(text, { exact: true }).first()).toBeVisible({ timeout: REAL_PRE_UI_TIMEOUT_MS });
      checked.push(text);
    }
    checked.push(await waitForAnyVisibleText(page, DOUYIN_ONE_CLICK_DASHBOARD_CHECKS, REAL_PRE_UI_TIMEOUT_MS));

    const bodyText = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
    const screenshotPath = join(EVIDENCE_DIR, 'screenshots', `${auth.username}-system_douyin-one_click.png`);
    await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => undefined);
    const fatalPageError = pageErrors.some((message) =>
      /Unexpected Application Error|Application Error|500|Bad Gateway/i.test(message)
    );
    assertTrue(!fatalPageError, `admin /system/douyin page errors: ${pageErrors.join('; ')}`);
    assertTrue(badResponses.length === 0, `admin /system/douyin one-click bad API responses: ${JSON.stringify(badResponses)}`);
    assertTrue(!/Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i.test(bodyText), 'admin /system/douyin shows runtime error');

    return { route: '/system/douyin', checked, badResponses, pageErrors, screenshot: screenshotPath };
  } finally {
    await context.close().catch(() => undefined);
  }
}

async function openPage(browser: Browser, auth: AuthState, route: string, shouldAllow: boolean): Promise<JsonMap> {
  const context = await newAuthContext(browser, auth);
  const page = await context.newPage();
  const badResponses: JsonMap[] = [];
  page.on('response', (response) => {
    const url = response.url();
    const status = response.status();
    if (url.includes('/api/') && ([401, 403].includes(status) || status >= 500)) {
      badResponses.push({ url: redactUrl(url), status });
    }
  });
  try {
    await page.goto(route, { waitUntil: 'domcontentloaded', timeout: REAL_PRE_NAV_TIMEOUT_MS });
    await waitForAppReady(page);
    const finalPath = new URL(page.url()).pathname;
    const bodyText = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
    const headerText = await page.locator('.top-header').innerText().catch(() => '');
    const sidebarText = await page.locator('[data-testid="sidebar-menu"]').innerText().catch(() => '');
    const screenshotPath = join(EVIDENCE_DIR, 'screenshots', `${auth.username}-${safeName(route)}-${shouldAllow ? 'allow' : 'deny'}.png`);
    await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => undefined);
    const runtimeError = /Unexpected Application Error|Application Error|Bad Gateway|Internal Server Error/i.test(bodyText);
    if (shouldAllow) {
      assertTrue(finalPath === route || finalPath.startsWith(`${route}/`), `${auth.username} expected ${route}, got ${finalPath}`);
      assertTrue(!runtimeError, `${auth.username} ${route} shows runtime error`);
      assertTrue(badResponses.length === 0, `${auth.username} ${route} has bad API responses: ${JSON.stringify(badResponses)}`);
    } else {
      assertTrue(finalPath !== route, `${auth.username} should not remain on forbidden route ${route}`);
    }
    return {
      route,
      shouldAllow,
      finalPath,
      badResponses,
      screenshot: screenshotPath,
      chromeText: [headerText, sidebarText].filter(Boolean).join('\n'),
      bodySample: bodyText.slice(0, 500)
    };
  } finally {
    await context.close().catch(() => undefined);
  }
}

async function waitForAppReady(page: import('@playwright/test').Page): Promise<void> {
  const bootLoading = page.locator('#boot-loading');
  await bootLoading.waitFor({ state: 'detached', timeout: REAL_PRE_UI_TIMEOUT_MS }).catch(async () => {
    await bootLoading.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => undefined);
  });
  await page.waitForLoadState('networkidle', { timeout: REAL_PRE_NETWORK_IDLE_TIMEOUT_MS }).catch(() => undefined);
  await page.locator('.n-spin-body').waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => undefined);
  await page.waitForTimeout(300);
}

async function waitForAnyVisibleText(
  page: import('@playwright/test').Page,
  texts: readonly string[],
  timeout: number
): Promise<string> {
  await expect
    .poll(async () => {
      for (const text of texts) {
        const visible = await page.getByText(text, { exact: true }).first().isVisible().catch(() => false);
        if (visible) return text;
      }
      return '';
    }, { timeout })
    .toBeTruthy();

  for (const text of texts) {
    const visible = await page.getByText(text, { exact: true }).first().isVisible().catch(() => false);
    if (visible) return text;
  }
  return texts[0] || '';
}

async function newAuthContext(browser: Browser, auth: AuthState): Promise<BrowserContext> {
  const context = await browser.newContext({ baseURL: FRONTEND, viewport: { width: 1440, height: 960 } });
  await context.addInitScript((payload: AuthState) => {
    localStorage.setItem('token', payload.token);
    if (payload.refreshToken) localStorage.setItem('refreshToken', payload.refreshToken);
    localStorage.setItem('userInfo', JSON.stringify(payload.user));
  }, auth);
  return context;
}

async function verifyAllowedApis(api: APIRequestContext, auth: AuthState, paths: string[]): Promise<JsonMap[]> {
  const results = [];
  for (const path of paths) {
    const result = await apiSuccess(api, 'GET', `/api${path.startsWith('/') ? path : `/${path}`}`, auth);
    results.push(summarizeResult(result));
  }
  return results;
}

async function expectForbiddenRoute(browser: Browser, auth: AuthState, route: string): Promise<void> {
  await openPage(browser, auth, route, false);
}

async function expectForbidden(
  api: APIRequestContext,
  auth: AuthState,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<void> {
  const result = await rawApi(api, method, path, auth, options);
  assertTrue(isForbidden(result), `expected forbidden for ${method} ${path}, got HTTP ${result.status}, code=${(result.body as JsonMap | undefined)?.code}`);
}

async function firstAssignableUser(api: APIRequestContext, auth: AuthState, keyword: string): Promise<JsonMap> {
  const result = await apiSuccess(api, 'GET', '/api/users/assignable', auth, { params: { keyword, page: 1, size: 20 } });
  const user = extractRecords(result.body).find((item) => String(item.username || '').includes(keyword)) || extractRecords(result.body)[0];
  if (!user?.id) {
    throw new Error(`assignable user not found: ${keyword}`);
  }
  return user;
}

async function apiSuccess(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  auth?: AuthState,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<{ status: number; ok: boolean; body: unknown; durationMs: number }> {
  const result = await rawApi(api, method, path, auth, options);
  if (!isSuccess(result)) {
    throw new Error(`${method} ${path} failed: HTTP ${result.status}, code=${(result.body as JsonMap | undefined)?.code}, msg=${(result.body as JsonMap | undefined)?.msg || (result.body as JsonMap | undefined)?.message || ''}`);
  }
  return result;
}

async function rawApi(
  api: APIRequestContext,
  method: 'GET' | 'POST' | 'PUT',
  path: string,
  auth?: AuthState,
  options: { data?: JsonMap; params?: JsonMap } = {}
): Promise<{ status: number; ok: boolean; body: unknown; durationMs: number }> {
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
      : await api.put(path, requestOptions);
  const body = await response.json().catch(async () => ({ rawText: await response.text().catch(() => '') }));
  return { status: response.status(), ok: response.ok(), body, durationMs: Date.now() - started };
}

function findProductCandidate(items: JsonMap[], status: string): JsonMap {
  const item = items.find((row) => String(row.bizStatus || 'PENDING_AUDIT') === status && (row.productId || row.product_id));
  if (!item) {
    throw new Error(`No product candidate with status ${status}`);
  }
  return item;
}

function readProductOperationStateSnapshot(activityId: string, productId: string): JsonMap {
  const container = process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = `
select coalesce(row_to_json(t)::text, '')
from (
  select
    audit_status,
    biz_status,
    audit_remark,
    audit_payload,
    bound_activity_id,
    assignee_id::text as assignee_id,
    promote_link,
    short_link,
    promotion_scene,
    external_unique_id,
    last_operation_at::text as last_operation_at,
    selected_to_library,
    selected_at::text as selected_at,
    selected_by::text as selected_by,
    deleted
  from product_operation_state
  where activity_id = ${sqlLiteral(activityId)}
    and product_id = ${sqlLiteral(productId)}
  limit 1
) t;`;
  const out = execFileSync('docker', ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-tAc', sql], {
    encoding: 'utf8'
  }).trim();
  return {
    activityId,
    productId,
    before: out ? JSON.parse(out) : null
  };
}

function readProductOperationLogIds(activityId: string, productId: string): string[] {
  const container = process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = process.env.E2E_DB_USER || 'saas';
  const db = process.env.E2E_DB_NAME || 'saas_real_pre';
  const sql = [
    'select id::text',
    'from product_operation_log',
    `where activity_id = ${sqlLiteral(activityId)}`,
    `and product_id = ${sqlLiteral(productId)}`,
    'order by create_time asc;'
  ].join(' ');
  const out = execFileSync('docker', ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-tAc', sql], {
    encoding: 'utf8'
  }).trim();
  return out ? out.split(/\r?\n/).map((line) => line.trim()).filter(Boolean) : [];
}

function diffNewIds(before: string[], after: string[]): string[] {
  const seen = new Set(before);
  return after.filter((id) => !seen.has(id));
}

function mergeUniqueStringArrays(left: string[], right: string[]): string[] {
  return Array.from(new Set([...left, ...right].filter(Boolean)));
}

function sqlLiteral(value: string): string {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function buildAuditPayload(label: string): JsonMap {
  return {
    approved: true,
    reason: `P3-5 ${label} product audit`,
    exclusivePriceRemark: 'P3-5 role smoke price context.',
    shippingInfo: 'P3-5 role smoke shipping info.',
    sellingPoints: ['P3-5 role smoke selling point A', 'P3-5 role smoke selling point B'],
    promotionScript: 'P3-5 role smoke promotion script.',
    supportsAds: true,
    rewardRemark: 'P3-5 role smoke reward remark.',
    participationRequirements: 'P3-5 role smoke participation requirement.',
    campaignTimeRemark: 'P3-5 role smoke campaign window.',
    materialFiles: ['p3-5-role-smoke-material']
  };
}

function requireAuth(run: RunState, role: string): AuthState {
  const auth = run.accounts[role];
  if (!auth) {
    throw new Error(`missing auth for ${role}`);
  }
  return auth;
}

function requireFlowString(run: RunState, key: string): string {
  const value = run.flow[key];
  if (!value) {
    throw new Error(`missing flow.${key}`);
  }
  return String(value);
}

function isSuccess(result: { status: number; ok: boolean; body: unknown }): boolean {
  const code = (result.body as JsonMap | undefined)?.code;
  return result.ok && (code === undefined || code === null || Number(code) === 200);
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

function summarizeResult(result: { status: number; ok: boolean; body: unknown; durationMs: number }): JsonMap {
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

function buildSummary(run: RunState): JsonMap {
  const cleanupVerified = run.flow.cleanupStatus === 'EXECUTED_AND_VERIFIED_ZERO_RESIDUAL';
  const conclusion = run.failures.length ? 'FAIL' : cleanupVerified ? 'PASS' : 'PASS_NEEDS_CLEANUP';
  return {
    evidenceType: 'real-pre-role-business-e2e',
    conclusion,
    startedAt: run.startedAt,
    finishedAt: run.finishedAt,
    frontend: FRONTEND,
    backend: BACKEND,
    evidenceDir: run.evidenceDir,
    overallPass: run.failures.length === 0,
    totalSteps: run.steps.length,
    passedSteps: run.steps.filter((step) => step.status === 'PASS').length,
    failedSteps: run.steps.filter((step) => step.status === 'FAIL').length,
    failures: run.failures,
    accounts: Object.fromEntries(Object.entries(run.accounts).map(([key, auth]) => [
      key,
      {
        username: auth.username,
        roleCodes: auth.roleCodes,
        dataScope: auth.dataScope,
        deptId: auth.deptId,
        userId: auth.userId
      }
    ])),
    flow: run.flow,
    steps: run.steps
  };
}

function writeEvidence(run: RunState): void {
  const summary = buildSummary(run);
  writeFileSync(join(run.evidenceDir, 'summary.json'), JSON.stringify(summary, null, 2), 'utf8');
  writeFileSync(join(run.evidenceDir, 'report.md'), buildReport(summary), 'utf8');
}

function buildReport(summary: JsonMap): string {
  const lines = [
    '# P3-5 real-pre role business flow',
    '',
    `- Conclusion: **${summary.conclusion}**`,
    `- Frontend: ${summary.frontend}`,
    `- Backend: ${summary.backend}`,
    `- Evidence: ${summary.evidenceDir}`,
    `- Steps: ${summary.passedSteps}/${summary.totalSteps} passed`,
    '',
    '## Accounts',
    ''
  ];
  const accounts = summary.accounts as Record<string, JsonMap>;
  for (const [role, auth] of Object.entries(accounts)) {
    lines.push(`- ${role}: ${auth.username} / ${(auth.roleCodes as string[]).join(',')} / dataScope=${auth.dataScope} / deptId=${auth.deptId || '-'}`);
  }
  lines.push('', '## Business Flow', '');
  const flow = summary.flow as JsonMap;
  for (const [key, value] of Object.entries(flow)) {
    lines.push(`- ${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`);
  }
  lines.push('', '## Steps', '', '| Step | Result | Detail |', '| --- | --- | --- |');
  for (const step of summary.steps as StepResult[]) {
    lines.push(`| ${step.id} ${step.title} | ${step.status} | ${step.error ? step.error.replace(/\|/g, '/') : ''} |`);
  }
  if ((summary.failures as string[]).length) {
    lines.push('', '## Failures', '');
    for (const failure of summary.failures as string[]) {
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

function safeName(value: string): string {
  return value.replace(/^\/+/, '').replace(/[^A-Za-z0-9_-]+/g, '_') || 'root';
}

function redactUrl(url: string): string {
  return url.replace(/[?&](access_token|refresh_token|token|client_secret)=[^&]+/gi, '$1=REDACTED');
}

function formatLocalTimestamp(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function formatLocalDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
