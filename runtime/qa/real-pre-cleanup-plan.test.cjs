const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildCleanupPlan,
  buildProductOperationStateRestoreSql
} = require('./real-pre-cleanup-plan.cjs');

test('buildCleanupPlan requires an explicit QA runId', () => {
  assert.throws(() => buildCleanupPlan({ runId: '' }), /RunId is required/);
  assert.throws(() => buildCleanupPlan({ runId: 'P35_123' }), /RunId must start with QA/);
});

test('buildCleanupPlan targets only QA local tables and never core real upstream tables', () => {
  const plan = buildCleanupPlan({
    runId: 'QA20260521_084500',
    state: {
      configKey: 'qa.journey.QA20260521_084500',
      talentId: 'talent-1',
      tempTalentId: 'talent-temp',
      sampleId: 'sample-1'
    },
    evidenceDir: 'runtime/qa/out/cleanup'
  });

  const tableNames = plan.targets.map((target) => target.table);
  assert.deepEqual(tableNames, [
    'system_config',
    'sample_status_log',
    'sample_request',
    'talent_claim',
    'talent',
    'pick_source_mapping',
    'promotion_link',
    'product_operation_log',
    'operation_log',
    'product_operation_state'
  ]);

  const combinedSql = [
    plan.planSql,
    plan.executeSql,
    plan.verifySql
  ].join('\n').toLowerCase();
  for (const forbidden of [
    'delete from colonelsettlement_order',
    'delete from product_snapshot',
    'delete from product ',
    'delete from douyin',
    'delete from sys_user'
  ]) {
    assert.equal(combinedSql.includes(forbidden), false, `forbidden cleanup SQL present: ${forbidden}`);
  }
});

test('buildCleanupPlan counts product state snapshots but verifies runId residuals', () => {
  const plan = buildCleanupPlan({
    runId: 'QA20260521_084500',
    state: {
      productOperationStateSnapshots: [
        { activityId: 'a1', productId: 'p1', before: { audit_status: '2' } }
      ]
    }
  });
  const target = plan.targets.find((item) => item.table === 'product_operation_state');
  assert.match(target.where, /activity_id = 'a1' AND product_id = 'p1'/i);
  assert.doesNotMatch(target.verifyWhere, /activity_id = 'a1' AND product_id = 'p1'/i);
  assert.match(target.verifyWhere, /audit_payload ILIKE '%QA20260521_084500%'/i);
});

test('buildProductOperationStateRestoreSql restores snapshots or deletes only run-tagged created state', () => {
  const restore = buildProductOperationStateRestoreSql({
    runId: 'QA20260521_084500',
    snapshot: {
      activityId: 'a1',
      productId: 'p1',
      before: {
        audit_status: '1',
        audit_remark: 'ok',
        audit_payload: '{"before":true}',
        promote_link: 'https://old.example',
        short_link: '',
        promotion_scene: '1',
        external_unique_id: 'old-ext',
        selected_to_library: 't',
        deleted: '0'
      }
    }
  });
  assert.match(restore, /update product_operation_state/i);
  assert.match(restore, /audit_payload = '\{"before":true\}'/i);
  assert.match(restore, /where activity_id = 'a1'/i);

  const remove = buildProductOperationStateRestoreSql({
    runId: 'QA20260521_084500',
    snapshot: {
      activityId: 'a1',
      productId: 'p2',
      before: null
    }
  });
  assert.match(remove, /delete from product_operation_state/i);
  assert.match(remove, /audit_payload ilike '%QA20260521_084500%'/i);
});
