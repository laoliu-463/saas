const fs = require('node:fs');
const path = require('node:path');

function buildCleanupPlan({ runId, state = {}, evidenceDir = '' }) {
  const normalizedRunId = normalizeRunId(runId);
  const normalizedState = state || {};
  const stateIds = collectStateIds(normalizedState);
  const runLike = ilikeLiteral(normalizedRunId);

  const configPredicate = orPredicate([
    stringEquals('config_key', normalizedState.configKey),
    `config_key ILIKE ${runLike}`,
    `config_value ILIKE ${runLike}`,
    `remark ILIKE ${runLike}`
  ]);
  const samplePredicate = orPredicate([
    uuidIn('id', [normalizedState.sampleId]),
    stringEquals('request_no', normalizedState.sampleRequestNo),
    `talent_uid ILIKE ${runLike}`,
    `talent_nickname ILIKE ${runLike}`,
    `remark ILIKE ${runLike}`,
    `extra_data::text ILIKE ${runLike}`
  ]);
  const talentPredicate = orPredicate([
    uuidIn('id', [normalizedState.talentId, normalizedState.tempTalentId]),
    stringEquals('douyin_uid', normalizedState.talentUid),
    stringEquals('douyin_uid', normalizedState.tempTalentUid),
    `douyin_uid ILIKE ${runLike}`,
    `nickname ILIKE ${runLike}`,
    `intro ILIKE ${runLike}`,
    `extra_data::text ILIKE ${runLike}`
  ]);
  const promotionPredicate = orPredicate([
    `talent_id ILIKE ${runLike}`,
    `talent_name ILIKE ${runLike}`,
    `pick_extra ILIKE ${runLike}`,
    `raw_response::text ILIKE ${runLike}`
  ]);
  const mappingPredicate = orPredicate([
    `talent_id ILIKE ${runLike}`,
    `talent_name ILIKE ${runLike}`,
    `source_url ILIKE ${runLike}`,
    `converted_url ILIKE ${runLike}`,
    `pick_extra ILIKE ${runLike}`,
    `promotion_link_id IN (SELECT id FROM promotion_link WHERE ${promotionPredicate})`
  ]);
  const productLogPredicate = orPredicate([
    `operation_payload ILIKE ${runLike}`,
    `operation_remark ILIKE ${runLike}`,
    uuidIn('id', normalizedState.productOperationLogIds)
  ]);
  const operationLogPredicate = orPredicate([
    `content ILIKE ${runLike}`,
    `target_name ILIKE ${runLike}`,
    `request_body::text ILIKE ${runLike}`,
    `response_body::text ILIKE ${runLike}`,
    `request_params::text ILIKE ${runLike}`,
    stringIn('target_id', stateIds)
  ]);
  const productStateSnapshots = normalizeProductOperationStateSnapshots(normalizedState);
  const productStatePredicate = orPredicate([
    `audit_payload ILIKE ${runLike}`,
    `external_unique_id ILIKE ${runLike}`,
    `promote_link ILIKE ${runLike}`,
    `short_link ILIKE ${runLike}`
  ]);
  const productStateSnapshotPredicate = orPredicate(productStateSnapshots.map((snapshot) =>
    `activity_id = ${sqlLiteral(snapshot.activityId)} AND product_id = ${sqlLiteral(snapshot.productId)}`
  ));
  const productStateAffectedPredicate = orPredicate([
    productStatePredicate,
    productStateSnapshotPredicate
  ]);

  const targets = [
    target('system_config', configPredicate, `DELETE FROM system_config WHERE ${configPredicate};`),
    target('sample_status_log', `request_id IN (SELECT id FROM sample_request WHERE ${samplePredicate})`, `DELETE FROM sample_status_log WHERE request_id IN (SELECT id FROM sample_request WHERE ${samplePredicate});`),
    target('sample_request', samplePredicate, `DELETE FROM sample_request WHERE ${samplePredicate};`),
    target('talent_claim', `talent_id IN (SELECT id FROM talent WHERE ${talentPredicate}) OR talent_uid ILIKE ${runLike}`, `DELETE FROM talent_claim WHERE talent_id IN (SELECT id FROM talent WHERE ${talentPredicate}) OR talent_uid ILIKE ${runLike};`),
    target('talent', talentPredicate, `DELETE FROM talent WHERE ${talentPredicate};`),
    target('pick_source_mapping', mappingPredicate, `DELETE FROM pick_source_mapping WHERE ${mappingPredicate};`),
    target('promotion_link', promotionPredicate, `DELETE FROM promotion_link WHERE ${promotionPredicate};`),
    target('product_operation_log', productLogPredicate, `DELETE FROM product_operation_log WHERE ${productLogPredicate};`),
    target('operation_log', operationLogPredicate, `DELETE FROM operation_log WHERE ${operationLogPredicate};`),
    target(
      'product_operation_state',
      productStateAffectedPredicate,
      buildProductOperationStateCleanupSql(normalizedRunId, normalizedState),
      productStatePredicate
    )
  ];

  const planSql = targets
    .map((item) => `SELECT ${sqlLiteral(item.table)} AS target_table, COUNT(*) AS matched_rows FROM ${item.table} WHERE ${item.where};`)
    .join('\nUNION ALL\n');
  const executeSql = [
    'BEGIN;',
    ...targets.map((item) => item.cleanupSql).filter(Boolean),
    'COMMIT;'
  ].join('\n');
  const verifySql = targets
    .map((item) => `SELECT ${sqlLiteral(item.table)} AS target_table, COUNT(*) AS remaining_rows FROM ${item.table} WHERE ${item.verifyWhere};`)
    .join('\nUNION ALL\n');

  return {
    runId: normalizedRunId,
    generatedAt: new Date().toISOString(),
    evidenceDir,
    stateFile: normalizedState.__stateFile || '',
    mode: 'PlanOnly',
    targets,
    protectedTables: [
      'colonelsettlement_order',
      'product_snapshot',
      'product',
      'colonel_activity',
      'douyin_token',
      'sys_user',
      'sys_role'
    ],
    planSql,
    executeSql,
    verifySql
  };
}

function buildProductOperationStateCleanupSql(runId, state) {
  const snapshots = normalizeProductOperationStateSnapshots(state);
  if (!snapshots.length) {
    return `DELETE FROM product_operation_state WHERE audit_payload ILIKE ${ilikeLiteral(runId)} OR external_unique_id ILIKE ${ilikeLiteral(runId)} OR promote_link ILIKE ${ilikeLiteral(runId)} OR short_link ILIKE ${ilikeLiteral(runId)};`;
  }
  return snapshots
    .map((snapshot) => buildProductOperationStateRestoreSql({ runId, snapshot }))
    .join('\n');
}

function buildProductOperationStateRestoreSql({ runId, snapshot }) {
  if (!snapshot || !snapshot.activityId || !snapshot.productId) {
    throw new Error('product operation state snapshot requires activityId and productId');
  }
  const where = `activity_id = ${sqlLiteral(snapshot.activityId)} AND product_id = ${sqlLiteral(snapshot.productId)}`;
  if (!snapshot.before) {
    return `DELETE FROM product_operation_state WHERE ${where} AND (audit_payload ILIKE ${ilikeLiteral(runId)} OR external_unique_id ILIKE ${ilikeLiteral(runId)} OR promote_link ILIKE ${ilikeLiteral(runId)} OR short_link ILIKE ${ilikeLiteral(runId)});`;
  }
  const before = snapshot.before;
  const assignments = [
    ['audit_status', before.audit_status],
    ['biz_status', before.biz_status],
    ['audit_remark', before.audit_remark],
    ['audit_payload', before.audit_payload],
    ['bound_activity_id', before.bound_activity_id],
    ['assignee_id', before.assignee_id],
    ['promote_link', before.promote_link],
    ['short_link', before.short_link],
    ['promotion_scene', before.promotion_scene],
    ['external_unique_id', before.external_unique_id],
    ['last_operation_at', before.last_operation_at],
    ['selected_to_library', before.selected_to_library],
    ['selected_at', before.selected_at],
    ['selected_by', before.selected_by],
    ['deleted', before.deleted]
  ].map(([column, value]) => `  ${column} = ${sqlValue(value)}`);
  return [
    'UPDATE product_operation_state SET',
    assignments.join(',\n'),
    `WHERE ${where};`
  ].join('\n');
}

function normalizeProductOperationStateSnapshots(state) {
  const snapshots = [];
  if (Array.isArray(state.productOperationStateSnapshots)) {
    snapshots.push(...state.productOperationStateSnapshots);
  }
  if (state.productOperationStateBefore !== undefined || state.selectedProductId || state.activityId) {
    snapshots.push({
      activityId: state.activityId,
      productId: state.selectedProductId,
      before: state.productOperationStateBefore ?? null
    });
  }
  return snapshots.filter((item) => item && item.activityId && item.productId);
}

function target(table, where, cleanupSql, verifyWhere = where) {
  const predicate = where || 'FALSE';
  const verifyPredicate = verifyWhere || predicate;
  return {
    table,
    where: predicate,
    verifyWhere: verifyPredicate,
    countSql: `SELECT COUNT(*) FROM ${table} WHERE ${predicate};`,
    previewSql: `SELECT * FROM ${table} WHERE ${predicate} LIMIT 50;`,
    cleanupSql,
    verifySql: `SELECT COUNT(*) FROM ${table} WHERE ${verifyPredicate};`
  };
}

function normalizeRunId(runId) {
  const value = String(runId || '').trim();
  if (!value) throw new Error('RunId is required');
  if (!/^QA[A-Za-z0-9_-]+$/.test(value)) {
    throw new Error('RunId must start with QA and contain only letters, numbers, underscore, or dash');
  }
  return value;
}

function collectStateIds(state) {
  return [
    state.configId,
    state.talentId,
    state.tempTalentId,
    state.sampleId,
    state.productLocalId,
    state.mappingId,
    state.promotionLinkId
  ].filter(Boolean).map(String);
}

function orPredicate(parts) {
  const filtered = parts.filter(Boolean);
  return filtered.length ? `(${filtered.join(' OR ')})` : 'FALSE';
}

function uuidIn(column, values) {
  const filtered = (values || []).filter(Boolean).map(String);
  if (!filtered.length) return '';
  return `${column} IN (${filtered.map((value) => `${sqlLiteral(value)}::uuid`).join(', ')})`;
}

function stringIn(column, values) {
  const filtered = (values || []).filter(Boolean).map(String);
  if (!filtered.length) return '';
  return `${column} IN (${filtered.map(sqlLiteral).join(', ')})`;
}

function stringEquals(column, value) {
  if (!value) return '';
  return `${column} = ${sqlLiteral(value)}`;
}

function ilikeLiteral(value) {
  return sqlLiteral(`%${value}%`);
}

function sqlValue(value) {
  if (value === null || value === undefined || value === '') return 'NULL';
  const text = String(value);
  if (/^-?\d+(\.\d+)?$/.test(text)) return text;
  if (/^(t|true)$/i.test(text)) return 'TRUE';
  if (/^(f|false)$/i.test(text)) return 'FALSE';
  return sqlLiteral(text);
}

function sqlLiteral(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function readStateFile(stateFile) {
  if (!stateFile) return {};
  const resolved = path.resolve(stateFile);
  const raw = JSON.parse(fs.readFileSync(resolved, 'utf8'));
  const state = raw && typeof raw === 'object' && raw.flow && typeof raw.flow === 'object'
    ? raw.flow
    : raw;
  state.__stateFile = resolved;
  return state;
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith('--')) continue;
    const key = arg.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith('--')) {
      args[key] = true;
    } else {
      args[key] = next;
      i += 1;
    }
  }
  return args;
}

if (require.main === module) {
  const args = parseArgs(process.argv.slice(2));
  const state = readStateFile(args['state-file']);
  const plan = buildCleanupPlan({
    runId: args['run-id'] || state.runId,
    state,
    evidenceDir: args['evidence-dir'] || ''
  });
  process.stdout.write(`${JSON.stringify(plan, null, 2)}\n`);
}

module.exports = {
  buildCleanupPlan,
  buildProductOperationStateRestoreSql,
  normalizeProductOperationStateSnapshots,
  normalizeRunId,
  sqlLiteral
};
