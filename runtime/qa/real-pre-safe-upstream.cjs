const { execFileSync } = require('node:child_process');
const {
  DEFAULT_REAL_PRE_DB_CONTAINER,
  DEFAULT_REAL_PRE_DB_NAME,
  DEFAULT_REAL_PRE_DB_USER
} = require('./real-pre-env.cjs');

function buildReusablePromotionMappingQuery({ activityId, productId, userId, limit = 5 }) {
  if (!activityId || !productId || !userId) {
    throw new Error('activityId, productId, and userId are required to reuse a promotion mapping');
  }
  const safeLimit = Number.isFinite(Number(limit)) ? Math.max(1, Math.min(20, Number(limit))) : 5;
  return [
    'select',
    '  psm.id::text as mapping_id,',
    '  psm.pick_source,',
    '  psm.product_id,',
    '  psm.activity_id,',
    '  psm.user_id::text as user_id,',
    "  coalesce(psm.promotion_link_id::text, '') as promotion_link_id,",
    "  coalesce(pl.promotion_url, psm.converted_url, '') as promotion_url,",
    "  coalesce(pl.short_url, '') as short_url,",
    '  psm.create_time::text as create_time',
    'from pick_source_mapping psm',
    'left join promotion_link pl',
    '  on pl.id = psm.promotion_link_id',
    ' and pl.deleted = 0',
    'where psm.deleted = 0',
    '  and psm.status = 1',
    "  and coalesce(psm.pick_source, '') <> ''",
    `  and psm.activity_id = ${sqlLiteral(activityId)}`,
    `  and psm.product_id = ${sqlLiteral(productId)}`,
    `  and psm.user_id = ${sqlLiteral(userId)}::uuid`,
    'order by psm.update_time desc nulls last, psm.create_time desc',
    `limit ${safeLimit};`
  ].join('\n');
}

function buildAnyReusablePromotionMappingQuery({ limit = 5, assigneeId = null } = {}) {
  const safeLimit = Number.isFinite(Number(limit)) ? Math.max(1, Math.min(20, Number(limit))) : 5;
  const lines = [
    'select',
    '  psm.id::text as mapping_id,',
    '  psm.pick_source,',
    '  psm.product_id,',
    '  psm.activity_id,',
    '  psm.user_id::text as user_id,',
    "  coalesce(psm.promotion_link_id::text, '') as promotion_link_id,",
    "  coalesce(pl.promotion_url, psm.converted_url, '') as promotion_url,",
    "  coalesce(pl.short_url, '') as short_url,",
    '  psm.create_time::text as create_time',
    'from pick_source_mapping psm',
    'left join promotion_link pl',
    '  on pl.id = psm.promotion_link_id',
    ' and pl.deleted = 0'
  ];

  if (assigneeId) {
    lines.push(
      'left join product_operation_state pos',
      '  on pos.product_id = psm.product_id',
      ' and pos.activity_id = psm.activity_id',
      ' and pos.deleted = 0'
    );
  }

  lines.push(
    'where psm.deleted = 0',
    '  and psm.status = 1',
    "  and coalesce(psm.pick_source, '') <> ''",
    "  and coalesce(pl.promotion_url, psm.converted_url, '') <> ''"
  );

  if (assigneeId) {
    lines.push(`  and pos.assignee_id = ${sqlLiteral(String(assigneeId))}::uuid`);
  }

  lines.push(
    'order by psm.update_time desc nulls last, psm.create_time desc',
    `limit ${safeLimit};`
  );

  return lines.join('\n');
}

function parsePipeRows(rows) {
  return (Array.isArray(rows) ? rows : String(rows || '').split(/\r?\n/))
    .map((row) => String(row || '').trim())
    .filter(Boolean)
    .map((row) => {
      const parts = row.split('|');
      return {
        mappingId: parts[0] || '',
        pickSource: parts[1] || '',
        productId: parts[2] || '',
        activityId: parts[3] || '',
        userId: parts[4] || '',
        promotionLinkId: parts[5] || '',
        promotionUrl: parts[6] || '',
        shortUrl: parts[7] || '',
        createTime: parts[8] || ''
      };
    });
}

function selectReusablePromotionMapping(rows) {
  const mapping = (rows || []).find((row) => row && row.pickSource);
  if (!mapping) {
    throw new Error('BLOCKED_REUSABLE_PROMOTION_MAPPING_MISSING: no reusable real-pre promotion mapping was found; refusing to create a new upstream promotion link.');
  }
  return mapping;
}

function buildPromotionBlockerMessage({ activityId, productId, userId }) {
  return [
    'BLOCKED_REUSABLE_PROMOTION_MAPPING_MISSING',
    `activityId=${activityId || ''}`,
    `productId=${productId || ''}`,
    `userId=${userId || ''}`,
    '未找到可复用 real-pre 推广映射，本步骤按生产安全上游模式阻塞，不调用真实上游创建转链。'
  ].join(' | ');
}

function queryReusablePromotionMapping(options) {
  const container = options.container || process.env.E2E_DB_CONTAINER || DEFAULT_REAL_PRE_DB_CONTAINER;
  const user = options.dbUser || process.env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
  const db = options.dbName || process.env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;
  const sql = buildReusablePromotionMappingQuery(options);
  const output = execFileSync(
    'docker',
    ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-t', '-A', '-F', '|', '-c', sql],
    { encoding: 'utf8' }
  );
  return parsePipeRows(output);
}

function queryAnyReusablePromotionMapping(options = {}) {
  const container = options.container || process.env.E2E_DB_CONTAINER || DEFAULT_REAL_PRE_DB_CONTAINER;
  const user = options.dbUser || process.env.E2E_DB_USER || DEFAULT_REAL_PRE_DB_USER;
  const db = options.dbName || process.env.E2E_DB_NAME || DEFAULT_REAL_PRE_DB_NAME;
  const sql = buildAnyReusablePromotionMappingQuery(options);
  const output = execFileSync(
    'docker',
    ['exec', container, 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', db, '-t', '-A', '-F', '|', '-c', sql],
    { encoding: 'utf8' }
  );
  return parsePipeRows(output);
}

function sqlLiteral(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

module.exports = {
  buildReusablePromotionMappingQuery,
  buildAnyReusablePromotionMappingQuery,
  parsePipeRows,
  selectReusablePromotionMapping,
  buildPromotionBlockerMessage,
  queryReusablePromotionMapping,
  queryAnyReusablePromotionMapping,
  sqlLiteral
};
