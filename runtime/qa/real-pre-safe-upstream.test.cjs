const test = require('node:test');
const assert = require('node:assert/strict');

const {
  buildReusablePromotionMappingQuery,
  parsePipeRows,
  selectReusablePromotionMapping,
  buildPromotionBlockerMessage
} = require('./real-pre-safe-upstream.cjs');

test('buildReusablePromotionMappingQuery only reads active local mapping and promotion rows', () => {
  const sql = buildReusablePromotionMappingQuery({
    activityId: '3916506',
    productId: '3810562766247428542',
    userId: '11111111-1111-1111-1111-111111111111'
  });

  assert.match(sql, /from pick_source_mapping psm/i);
  assert.match(sql, /left join promotion_link pl/i);
  assert.match(sql, /psm\.deleted = 0/i);
  assert.match(sql, /psm\.status = 1/i);
  assert.match(sql, /psm\.activity_id = '3916506'/i);
  assert.match(sql, /psm\.product_id = '3810562766247428542'/i);
  assert.match(sql, /psm\.user_id = '11111111-1111-1111-1111-111111111111'/i);
  assert.doesNotMatch(sql, /\b(insert|update|delete)\b|instPickSourceConvert|promotion-links/i);
});

test('parsePipeRows maps reusable promotion rows into stable objects', () => {
  const rows = parsePipeRows([
    'map-1|v.MxZLIw|3810562766247428542|3916506|user-1|plink-1|https://example.test/p?pick_source=v.MxZLIw|https://short.test/a|2026-05-17 10:00:00'
  ]);

  assert.deepEqual(rows, [
    {
      mappingId: 'map-1',
      pickSource: 'v.MxZLIw',
      productId: '3810562766247428542',
      activityId: '3916506',
      userId: 'user-1',
      promotionLinkId: 'plink-1',
      promotionUrl: 'https://example.test/p?pick_source=v.MxZLIw',
      shortUrl: 'https://short.test/a',
      createTime: '2026-05-17 10:00:00'
    }
  ]);
});

test('selectReusablePromotionMapping blocks instead of creating real upstream promotion links', () => {
  const mapping = selectReusablePromotionMapping([
    {
      mappingId: 'map-1',
      pickSource: 'v.MxZLIw',
      productId: 'p1',
      activityId: 'a1',
      userId: 'u1'
    }
  ]);
  assert.equal(mapping.pickSource, 'v.MxZLIw');

  assert.throws(
    () => selectReusablePromotionMapping([]),
    /BLOCKED_REUSABLE_PROMOTION_MAPPING_MISSING/
  );
  assert.match(
    buildPromotionBlockerMessage({ activityId: 'a1', productId: 'p1', userId: 'u1' }),
    /不调用真实上游创建转链/
  );
});
