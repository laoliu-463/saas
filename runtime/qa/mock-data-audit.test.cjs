const test = require('node:test');
const assert = require('node:assert/strict');

const {
  normalizeEnv,
  unwrapApiBody,
  extractRecords,
  evaluateScenarioGroup,
  buildSeedSuggestions,
  inferPageCoverageFromReports,
  isHardFailureScenario,
  evaluateAll
} = require('./mock-data-audit.cjs');

test('normalizeEnv accepts only TEST or test profiles with mock switches', () => {
  const testEnv = normalizeEnv({
    data: {
      environmentLabel: 'TEST',
      activeProfiles: ['test'],
      appTestEnabled: true,
      douyinTestEnabled: true,
      database: 'saas_test'
    }
  });
  assert.equal(testEnv.isTest, true);
  assert.equal(testEnv.isRealPre, false);

  const realPre = normalizeEnv({
    data: {
      environmentLabel: 'REAL-PRE',
      activeProfiles: ['real-pre'],
      appTestEnabled: false,
      douyinTestEnabled: false
    }
  });
  assert.equal(realPre.isTest, false);
  assert.equal(realPre.isRealPre, true);
});

test('unwrapApiBody and extractRecords handle common ApiResult shapes', () => {
  assert.deepEqual(unwrapApiBody({ code: 200, data: { x: 1 } }), { x: 1 });
  assert.deepEqual(extractRecords({ code: 200, data: { records: [{ id: 1 }], total: 1 } }), [{ id: 1 }]);
  assert.deepEqual(extractRecords({ code: 200, data: { list: [{ id: 2 }] } }), [{ id: 2 }]);
  assert.deepEqual(extractRecords({ code: 200, data: [{ id: 3 }] }), [{ id: 3 }]);
});

test('evaluateScenarioGroup separates required failures from special warnings', () => {
  const matrixGroup = {
    id: 'orders',
    label: '订单与归因',
    requiredAttributionCases: ['ATTRIBUTED', 'NO_PICK_SOURCE'],
    specialCases: ['EXCLUSIVE_TALENT']
  };
  const counters = {
    requiredAttributionCases: { ATTRIBUTED: 2, NO_PICK_SOURCE: 0 },
    specialCases: { EXCLUSIVE_TALENT: 0 }
  };
  const result = evaluateScenarioGroup(matrixGroup, counters, { apiCovered: true, pageCovered: 'unknown' });
  assert.equal(result.requiredCount, 2);
  assert.equal(result.actualCount, 1);
  assert.equal(result.dataCovered, false);
  assert.equal(result.missingRequired[0].scenario, 'NO_PICK_SOURCE');
  assert.equal(result.missingSpecial[0].severity, 'warning');
  assert.equal(isHardFailureScenario(result.missingRequired[0]), true);
  assert.equal(isHardFailureScenario(result.missingSpecial[0]), false);
});

test('buildSeedSuggestions emits actionable seed rows for missing scenarios', () => {
  const suggestions = buildSeedSuggestions([
    {
      moduleId: 'products',
      moduleLabel: '商品与活动',
      group: 'requiredStatuses',
      scenario: 'LISTED',
      severity: 'fail'
    },
    {
      moduleId: 'orders',
      moduleLabel: '订单与归因',
      group: 'specialCases',
      scenario: 'EXCLUSIVE_MERCHANT',
      severity: 'warning'
    }
  ]);
  assert.equal(suggestions.length, 2);
  assert.match(suggestions[0].seedKey, /products_requiredStatuses_LISTED/);
  assert.match(suggestions[0].suggestion, /LISTED/);
  assert.equal(suggestions[1].severity, 'warning');
});

test('inferPageCoverageFromReports maps existing QA reports to module ids', () => {
  const reports = [
    {
      name: 'qa-business-browser-flow',
      overallPass: true,
      steps: [
        { name: '商品库', pass: true, skipped: false },
        { name: '商品审核 / 状态动作', pass: true, skipped: true },
        { name: '订单列表', pass: true, skipped: false }
      ]
    },
    {
      script: 'qa-page-role-smoke',
      overallPass: true,
      allRouteResults: [
        { route: '/system/users', finalPath: '/system/users', hasApi500: false },
        { route: '/talent', finalPath: '/talent', hasApi500: false }
      ]
    }
  ];
  const coverage = inferPageCoverageFromReports(reports);
  assert.equal(coverage.products, 'partial');
  assert.equal(coverage.orders, true);
  assert.equal(coverage.system, true);
  assert.equal(coverage.talents, true);
  assert.equal(coverage.samples, 'unknown');
});

test('evaluateAll recognizes TEST mock hard-gap seed shapes from API-visible fields', () => {
  const matrix = {
    modules: [
      {
        id: 'products',
        label: '商品与活动',
        requiredStatuses: ['APPROVED', 'REJECTED'],
        specialCases: []
      },
      {
        id: 'talents',
        label: '达人',
        requiredStatuses: ['PRIVATE', 'PROTECTION_ACTIVE', 'PROTECTION_EXPIRED'],
        specialCases: ['multi_claim']
      },
      {
        id: 'samples',
        label: '寄样',
        requiredStatuses: ['PENDING_REVIEW', 'SHIPPING'],
        specialCases: ['duplicate_within_7_days', 'completed_by_order', 'talent_not_qualified_but_reason_filled']
      },
      {
        id: 'orders',
        label: '订单与归因',
        requiredAttributionCases: ['PRODUCT_UNCOVERED', 'REFUNDED_OR_CLOSED'],
        specialCases: ['cross_month', 'zero_service_fee', 'order_created_before_mapping', 'order_created_after_mapping']
      },
      {
        id: 'dashboard',
        label: '数据平台',
        requiredCases: ['cross_month_data', 'empty_date_range', 'filter_by_talent', 'filter_by_activity']
      },
      {
        id: 'system',
        label: '系统管理',
        requiredCases: ['user_disabled_case']
      }
    ]
  };
  const now = new Date();
  const nextMonth = new Date(now);
  nextMonth.setMonth(nextMonth.getMonth() + 1);
  const expired = new Date(now);
  expired.setDate(expired.getDate() - 5);

  const data = {
    products: [
      { bizStatus: 'APPROVED', auditStatus: 2, activityId: 'TEST_ACTIVITY_A' },
      { bizStatus: 'REJECTED', auditStatus: 3, activityId: 'TEST_ACTIVITY_A' }
    ],
    productPicks: [],
    activityProducts: [],
    activityProductGroups: {},
    activities: [],
    colonelActivities: [],
    talents: [
      { poolStatus: 'PUBLIC', protectedUntil: expired.toISOString(), ownerName: '已过期释放' }
    ],
    talentPublic: [],
    talentPrivate: [
      {
        poolStatus: 'PRIVATE',
        protectedUntil: nextMonth.toISOString(),
        ownerCount: 2,
        nickname: '达人私海样本',
        douyinNo: 'dy_private',
        douyinUid: 'talent_private',
        fansCount: 10000
      }
    ],
    samples: [
      {
        id: 'sample-pending-review',
        productId: 'p1',
        talentId: 't1',
        status: 1,
        createTime: now.toISOString(),
        remark: '资格不足但已填写申请原因',
        applyReason: '资格不足但业务需人工复核'
      },
      {
        id: 'sample-shipping',
        productId: 'p1',
        talentId: 't1',
        status: 3,
        trackingNo: 'SF123',
        createTime: now.toISOString(),
        remark: '快递中'
      },
      {
        id: 'sample-completed',
        productId: 'p2',
        talentId: 't2',
        status: 6,
        createTime: now.toISOString(),
        remark: '命中订单自动完成'
      }
    ],
    orders: [
      {
        orderId: 'order-product-uncovered',
        attributionStatus: 'UNATTRIBUTED',
        attributionRemark: 'PRODUCT_UNCOVERED',
        orderStatus: 1,
        orderAmount: 1000,
        settleColonelCommission: 0,
        createTime: now.toISOString(),
        settleTime: nextMonth.toISOString(),
        mappingCreateTime: nextMonth.toISOString(),
        talentId: 'talent-private-id',
        activityId: 'TEST_ACTIVITY_A'
      },
      {
        orderId: 'order-refunded',
        attributionStatus: 'UNATTRIBUTED',
        attributionRemark: '退款关闭订单',
        orderStatus: 4,
        orderAmount: 2000,
        serviceFee: 100,
        createTime: now.toISOString(),
        settleTime: now.toISOString(),
        mappingCreateTime: expired.toISOString(),
        talentId: 'talent-private-id',
        activityId: 'TEST_ACTIVITY_A'
      }
    ],
    unattributedOrders: [],
    dataOrders: [
      { id: 'data-order', talentId: 'talent-private-id', activityId: 'TEST_ACTIVITY_A' }
    ],
    emptyDateRangeOrders: [],
    filterOptions: {},
    users: [
      { username: 'disabled_user', status: 0 }
    ],
    roles: [],
    configs: {},
    operationLogs: []
  };
  const apiAvailability = {
    products: true,
    activities: true,
    talents: true,
    samples: true,
    orders: true,
    orderFilterOptions: true,
    dashboardSummary: true,
    users: true,
    roles: true,
    configs: true
  };
  const evaluated = evaluateAll(matrix, data, {}, apiAvailability);
  assert.deepEqual(evaluated.results.flatMap((item) => item.missingRequired), []);
  assert.equal(evaluated.results.every((item) => item.dataCovered), true);
  assert.equal(evaluated.countersByModule.orders.specialCases.zero_service_fee, 1);
});
