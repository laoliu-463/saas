const test = require('node:test');
const assert = require('node:assert/strict');

const {
  STATUS_LABELS,
  buildAuditPayload,
  evaluateExpectation,
  summarizeCaseResults,
  pickKeyFields
} = require('./business-state-flow-regression.cjs');

test('business status labels include product, sample, and order states used by the acceptance flow', () => {
  assert.equal(STATUS_LABELS.PENDING_AUDIT, '待审核');
  assert.equal(STATUS_LABELS.APPROVED, '已上架');
  assert.equal(STATUS_LABELS.PENDING_SHIP, '待发货');
  assert.equal(STATUS_LABELS.PENDING_HOMEWORK, '待交作业');
  assert.equal(STATUS_LABELS.ATTRIBUTED, '已归因');
});

test('buildAuditPayload returns the complete supplement required by product approval', () => {
  const payload = buildAuditPayload('QA123');

  assert.equal(payload.approved, true);
  assert.equal(payload.supportsAds, true);
  assert.ok(payload.sellingPoints.length >= 2);
  assert.ok(payload.materialFiles.length >= 1);
  assert.match(payload.reason, /QA123/);
});

test('evaluateExpectation handles positive and forbidden API outcomes', () => {
  const success = evaluateExpectation(
    { expect: { ok: true, field: 'bizStatus', equals: 'APPROVED' } },
    { ok: true, status: 200, body: { code: 200, data: { bizStatus: 'APPROVED' } } }
  );
  const forbidden = evaluateExpectation(
    { expect: { forbidden: true } },
    { ok: false, status: 403, body: { code: 403, msg: 'forbidden' } }
  );
  const failed = evaluateExpectation(
    { expect: { ok: true, field: 'status', equals: 'FINISHED' } },
    { ok: true, status: 200, body: { code: 200, data: { status: 'PENDING_HOMEWORK' } } }
  );

  assert.equal(success.pass, true);
  assert.equal(forbidden.pass, true);
  assert.equal(failed.pass, false);
  assert.match(failed.reason, /status/);
});

test('summarizeCaseResults records overall pass and hard failure reasons', () => {
  const summary = summarizeCaseResults([
    { name: 'ok-case', pass: true },
    { name: 'bad-case', pass: false, failureReason: 'expected 403' }
  ]);

  assert.equal(summary.total, 2);
  assert.equal(summary.pass, 1);
  assert.equal(summary.fail, 1);
  assert.equal(summary.overallPass, false);
  assert.deepEqual(summary.failures, [{ name: 'bad-case', failureReason: 'expected 403' }]);
});

test('pickKeyFields extracts nested status and attribution fields without copying full responses', () => {
  const fields = pickKeyFields({
    code: 200,
    data: {
      orderId: 'O1',
      attributionStatus: 'UNATTRIBUTED',
      unattributedReason: 'NO_PICK_SOURCE',
      nested: {
        bizStatus: 'REJECTED'
      }
    }
  });

  assert.equal(fields.orderId, 'O1');
  assert.equal(fields.attributionStatus, 'UNATTRIBUTED');
  assert.equal(fields.unattributedReason, 'NO_PICK_SOURCE');
  assert.equal(fields.bizStatus, 'REJECTED');
});
