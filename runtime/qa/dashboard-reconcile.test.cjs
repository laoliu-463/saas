const test = require('node:test');
const assert = require('node:assert/strict');

const {
  centToYuan,
  aggregateOrders,
  compareMetric,
  parsePsqlTsv,
  buildReconcileSummary,
  selectDbContainer
} = require('./dashboard-reconcile.cjs');

test('centToYuan normalizes cent amounts to two decimal numbers', () => {
  assert.equal(centToYuan(12345), 123.45);
  assert.equal(centToYuan('0'), 0);
  assert.equal(centToYuan(null), 0);
});

test('aggregateOrders computes core dashboard counters from order details', () => {
  const orders = [
    { orderAmount: 1000, settleColonelCommission: 120, attributionStatus: 'ATTRIBUTED', channelUserName: '渠道A', colonelUserName: '招商A' },
    { orderAmount: 2000, settleColonelCommission: 0, attributionStatus: 'UNATTRIBUTED', attributionRemark: 'NO_PICK_SOURCE', channelUserName: '渠道B', colonelUserName: '招商A' },
    { orderAmount: 3000, settleColonelCommission: 200, attributionStatus: 'UNATTRIBUTED', attributionRemark: 'PRODUCT_NOT_FOUND', orderStatus: 4 }
  ];

  const aggregate = aggregateOrders(orders);

  assert.equal(aggregate.orderCount, 3);
  assert.equal(aggregate.gmv, 60);
  assert.equal(aggregate.serviceFee, 3.2);
  assert.equal(aggregate.attributed, 1);
  assert.equal(aggregate.unattributed, 2);
  assert.equal(aggregate.productUncovered, 1);
  assert.equal(aggregate.refundClosed, 1);
  assert.equal(aggregate.byBiz['招商A'].orderCount, 2);
  assert.equal(aggregate.byChannel['渠道A'].orderCount, 1);
});

test('compareMetric supports exact integer and decimal tolerance checks', () => {
  assert.equal(compareMetric('orders', 10, 10).pass, true);
  assert.equal(compareMetric('gmv', 10.01, 10.01001, 0.01).pass, true);
  assert.equal(compareMetric('serviceFee', 10, 11, 0.01).pass, false);
});

test('parsePsqlTsv parses docker psql aggregate rows', () => {
  const rows = parsePsqlTsv('order_count\tgmv_cent\n2\t1234\n');

  assert.deepEqual(rows, [{ order_count: '2', gmv_cent: '1234' }]);
});

test('buildReconcileSummary fails when any metric mismatch remains', () => {
  const summary = buildReconcileSummary([
    { name: 'todayOrderCount', pass: true },
    { name: 'gmv', pass: false, apiValue: 1, dbValue: 2 }
  ]);

  assert.equal(summary.overallPass, false);
  assert.equal(summary.failedMetrics.length, 1);
});

test('selectDbContainer prefers explicit env, then compose-detected active postgres', () => {
  assert.equal(
    selectDbContainer({ QA_DB_CONTAINER: 'custom-postgres' }, 'saas-active-postgres-1'),
    'custom-postgres'
  );
  assert.equal(
    selectDbContainer({}, 'saas-active-postgres-1'),
    'saas-active-postgres-1'
  );
  assert.equal(
    selectDbContainer({}, ''),
    'saas-active-postgres-1'
  );
});
