const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  buildSaasCreatePayload,
  buildSaasManualFillPayload,
  collectFromInputFiles,
  ensureApplyAllowed,
  ensureManualInputOnly,
  hasBlockingBaiyingApiDiagnostic,
  hasManualInput,
  hasUsableTalentSignal,
  isBlockingBaiyingApiDiagnostic,
  maskContactValue,
  normalizeBrowserChannel,
  normalizeTalentRecord,
  parseArgs,
  parseChineseCount,
  parseTalentCardsFromHtml,
  redactTalentRecord,
  resolveCollectionSource,
  summarizeBaiyingApiResponse,
  toCsv
} = require('./baiying-talent-collect.cjs');

test('parseChineseCount converts common Baiying count units', () => {
  assert.equal(parseChineseCount('1.2万'), 12000);
  assert.equal(parseChineseCount('3,456'), 3456);
  assert.equal(parseChineseCount('2.5亿'), 250000000);
  assert.equal(parseChineseCount('粉丝 8.8w'), 88000);
  assert.equal(parseChineseCount('暂无'), null);
});

test('maskContactValue redacts phone and open contact values', () => {
  assert.equal(maskContactValue('13812345678'), '138****5678');
  assert.equal(maskContactValue('wxid_baiying_2026'), 'wx*************26');
  assert.equal(maskContactValue('ab'), '**');
  assert.equal(maskContactValue(''), '');
});

test('normalizeTalentRecord keeps sensitive contact only when explicitly allowed', () => {
  const raw = {
    nickname: ' 达人A ',
    douyinAccount: ' dy_a ',
    fansCount: '1.2万',
    likeCount: '3万',
    worksCount: '12',
    contactPhone: '13812345678',
    sourceUrl: 'https://buyin.jinritemai.com/talent/a'
  };

  const safe = normalizeTalentRecord(raw, { includeSensitive: false });
  assert.equal(safe.nickname, '达人A');
  assert.equal(safe.fansCount, 12000);
  assert.equal(safe.likeCount, 30000);
  assert.equal(safe.worksCount, 12);
  assert.equal(safe.contactPhone, '138****5678');
  assert.equal(safe.hasSensitiveContact, true);

  const sensitive = normalizeTalentRecord(raw, { includeSensitive: true });
  assert.equal(sensitive.contactPhone, '13812345678');
});

test('parseTalentCardsFromHtml extracts mock Baiying card fields', () => {
  const html = `
    <section class="talent-card">
      <h3>达人A</h3>
      <span>抖音号：dy_a</span>
      <span>UID：700001</span>
      <span>粉丝 1.2万</span>
      <span>获赞 3万</span>
      <span>作品 88</span>
      <span>IP：上海</span>
      <span>主营类目：美妆</span>
      <span>联系方式：13812345678</span>
    </section>
    <section class="talent-card">
      <h3>达人B</h3>
      <span>抖音号：dy_b</span>
      <span>粉丝 9000</span>
    </section>
  `;

  const records = parseTalentCardsFromHtml(html, { includeSensitive: false });
  assert.equal(records.length, 2);
  assert.equal(records[0].nickname, '达人A');
  assert.equal(records[0].douyinAccount, 'dy_a');
  assert.equal(records[0].talentUid, '700001');
  assert.equal(records[0].fansCount, 12000);
  assert.equal(records[0].likeCount, 30000);
  assert.equal(records[0].worksCount, 88);
  assert.equal(records[0].ipLocation, '上海');
  assert.equal(records[0].mainCategory, '美妆');
  assert.equal(records[0].contactPhone, '138****5678');
  assert.equal(records[1].nickname, '达人B');
});

test('parseTalentCardsFromHtml ignores Baiying filter and empty-state containers', () => {
  const html = `
    <div class="daren-square">
      <div>找达人</div>
      <div>全部达人</div>
      <div>直播达人</div>
      <div>短视频达人</div>
      <input placeholder="请输入达人昵称或商品名称" />
      <div>粉丝数据</div>
      <div>粉丝数</div>
      <div>其他筛选</div>
      <div>有联系方式</div>
      <div>暂无数据</div>
    </div>
  `;

  assert.deepEqual(parseTalentCardsFromHtml(html), []);
});

test('parseArgs accepts runtime search and repeated filter clicks', () => {
  const args = parseArgs([
    'dry-run',
    '--search-keyword',
    '上衣',
    '--filter-click',
    '有联系方式',
    '--filter-click=纯佣达人',
    '--navigation-click',
    '全部达人',
    '--clear-search',
    '--browser-channel',
    'chrome'
  ], {});

  assert.equal(args.mode, 'dry-run');
  assert.equal(args.searchKeyword, '上衣');
  assert.equal(args.clearSearch, true);
  assert.equal(args.browserChannel, 'chrome');
  assert.deepEqual(args.filterClicks, ['有联系方式', '纯佣达人']);
  assert.deepEqual(args.navigationClicks, ['全部达人']);
});

test('manual-import parses local HTML without Baiying browser access', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'baiying-manual-import-'));
  const htmlPath = path.join(tmpDir, 'page.html');
  fs.writeFileSync(htmlPath, `
    <section class="talent-card">
      <h3>手动达人</h3>
      <span>抖音号：manual_dy</span>
      <span>粉丝 2万</span>
      <span>联系方式：13812345678</span>
    </section>
  `, 'utf8');
  const args = parseArgs(['manual-import', '--input-html', htmlPath, '--max', '10'], {});
  const run = { steps: [] };
  const records = collectFromInputFiles(args, tmpDir, run);

  assert.equal(args.mode, 'manual-import');
  assert.equal(hasManualInput(args), true);
  assert.equal(records.length, 1);
  assert.equal(records[0].nickname, '手动达人');
  assert.equal(records[0].douyinAccount, 'manual_dy');
  assert.equal(records[0].fansCount, 20000);
  assert.equal(records[0].contactPhone, '138****5678');
  assert.equal(run.steps[0].step, 'manualInputHtml');
  assert.equal(fs.existsSync(path.join(tmpDir, 'manual-input-preview.redacted.json')), true);
});

test('manual-import keeps JSON source file out of profileUrl', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'baiying-manual-json-'));
  const jsonPath = path.join(tmpDir, 'records.json');
  fs.writeFileSync(jsonPath, JSON.stringify([
    {
      nickname: 'JSON达人',
      douyinAccount: 'json_dy',
      fansCount: '1万'
    }
  ]), 'utf8');
  const args = parseArgs(['manual-import', '--input-json', jsonPath], {});
  const records = collectFromInputFiles(args, tmpDir, { steps: [] });

  assert.equal(records.length, 1);
  assert.equal(records[0].profileUrl, '');
  assert.equal(records[0].sourceUrl, jsonPath);
});

test('Baiying direct browser automation requires local manual input', () => {
  assert.throws(
    () => ensureManualInputOnly(parseArgs(['dry-run'], {})),
    /direct browser automation is disabled/
  );
  assert.throws(
    () => ensureManualInputOnly(parseArgs(['manual-import'], {})),
    /manual-import mode requires --input-html or --input-json/
  );
  assert.doesNotThrow(
    () => ensureManualInputOnly(parseArgs(['apply', '--input-json', 'baiying-visible-talents.json'], {}))
  );
});

test('local browser capture is explicit and never enables direct apply', () => {
  const capture = parseArgs([
    'capture',
    '--enable-browser-capture',
    '--proxy-server',
    'http://127.0.0.1:7890',
    '--storage-state',
    'runtime/auth/douyin-storage-state.json',
    '--save-storage-state',
    'runtime/auth/douyin-storage-state.next.json'
  ], {});

  assert.equal(capture.enableBrowserCapture, true);
  assert.equal(capture.proxyServer, 'http://127.0.0.1:7890');
  assert.equal(capture.storageStatePath, 'runtime/auth/douyin-storage-state.json');
  assert.equal(capture.saveStorageStatePath, 'runtime/auth/douyin-storage-state.next.json');
  assert.equal(resolveCollectionSource(capture), 'browser-capture');

  assert.throws(
    () => resolveCollectionSource(parseArgs(['capture'], {})),
    /direct browser automation is disabled/
  );
  assert.throws(
    () => resolveCollectionSource(parseArgs(['apply', '--enable-browser-capture'], {})),
    /apply mode requires --input-json or --input-html/
  );
});

test('parseArgs uses conservative Baiying collection defaults', () => {
  const defaults = parseArgs(['dry-run'], {});
  assert.equal(defaults.maxRecords, 1);
  assert.equal(defaults.delayMs, 20000);
  assert.equal(defaults.initialDwellMs, 30000);
  assert.equal(defaults.scrollPages, 1);

  const clamped = parseArgs(['dry-run', '--delay-ms', '100'], {});
  assert.equal(clamped.delayMs, 15000);

  const noDwell = parseArgs(['dry-run', '--no-initial-dwell'], {});
  assert.equal(noDwell.initialDwellMs, 0);

  const manual = parseArgs(['dry-run', '--manual-ready', '--initial-dwell-ms', '100'], {});
  assert.equal(manual.manualReady, true);
  assert.equal(manual.initialDwellMs, 15000);
});

test('normalizeBrowserChannel accepts installed browser channels only', () => {
  assert.equal(normalizeBrowserChannel(''), '');
  assert.equal(normalizeBrowserChannel('chromium'), '');
  assert.equal(normalizeBrowserChannel('Chrome'), 'chrome');
  assert.equal(normalizeBrowserChannel('msedge'), 'msedge');
  assert.throws(() => normalizeBrowserChannel('firefox'), /Unsupported browser channel/);
});

test('hasUsableTalentSignal requires identity, contact, or numeric metrics', () => {
  assert.equal(hasUsableTalentSignal({ nickname: '找达人' }), false);
  assert.equal(hasUsableTalentSignal({ nickname: '达人A', fansCount: 1000 }), true);
  assert.equal(hasUsableTalentSignal({ nickname: '达人A', douyinAccount: 'dy_a' }), true);
  assert.equal(hasUsableTalentSignal({ nickname: '达人A', contactPhone: '138****5678' }), true);
});

test('summarizeBaiyingApiResponse keeps business network errors visible', () => {
  const summary = summarizeBaiyingApiResponse(
    'https://buyin.jinritemai.com/api/authorStatData/seekAuthor?msToken=secret&uid=700001',
    200,
    JSON.stringify({ code: 11001, st: 11001, msg: '当前网络不稳定，请稍后再试' })
  );

  assert.equal(summary.status, 200);
  assert.equal(summary.code, 11001);
  assert.equal(summary.message, '当前网络不稳定，请稍后再试');
  assert.match(summary.url, /msToken=%5Bredacted%5D/);
  assert.match(summary.url, /uid=%5Bredacted%5D/);

  assert.equal(summarizeBaiyingApiResponse('https://example.test/ok', 200, JSON.stringify({ code: 0, msg: 'success' })), null);
});

test('Baiying 9001 abnormal-operation diagnostics are blocking', () => {
  const summary = summarizeBaiyingApiResponse(
    'https://buyin.jinritemai.com/api/authorStatData/seekAuthor',
    200,
    JSON.stringify({ code: 9001, st: 9001, msg: '异常操作' })
  );

  assert.equal(summary.code, 9001);
  assert.equal(summary.message, '异常操作');
  assert.equal(isBlockingBaiyingApiDiagnostic(summary), true);
  assert.equal(hasBlockingBaiyingApiDiagnostic({ apiDiagnostics: [summary] }), true);
  assert.equal(isBlockingBaiyingApiDiagnostic({ code: 11001, message: '当前网络不稳定，请稍后再试' }), false);
});

test('buildSaasCreatePayload maps only public CRM fields and keeps raw contactWechat out', () => {
  const record = normalizeTalentRecord({
    nickname: '达人A',
    douyinAccount: 'dy_a',
    talentUid: '700001',
    fansCount: 12000,
    mainCategory: '美妆',
    contactPhone: '13812345678',
    contactWechat: 'wx_should_not_write'
  }, { includeSensitive: true });

  const payload = buildSaasCreatePayload(record);
  assert.equal(payload.nickname, '达人A');
  assert.equal(payload.douyinUid, '700001');
  assert.equal(payload.douyinNo, 'dy_a');
  assert.equal(payload.uid, '700001');
  assert.equal(payload.fansCount, 12000);
  assert.equal(payload.categories, '美妆');
  assert.equal(payload.contactPhone, '13812345678');
  assert.equal(Object.prototype.hasOwnProperty.call(payload, 'contactWechat'), false);
  assert.equal(Object.prototype.hasOwnProperty.call(payload, 'rawPayload'), false);
});

test('buildSaasManualFillPayload maps enrichment fields without internal contactWechat', () => {
  const record = normalizeTalentRecord({
    nickname: '达人A',
    fansCount: 12000,
    likeCount: 30000,
    followingCount: 50,
    worksCount: 88,
    ipLocation: '上海',
    talentLevel: 'LV3',
    avatarUrl: 'https://example.test/a.png',
    contactPhone: '13812345678',
    contactWechat: 'wx_should_not_write'
  }, { includeSensitive: true });

  const payload = buildSaasManualFillPayload(record);
  assert.equal(payload.nickname, '达人A');
  assert.equal(payload.fansCount, 12000);
  assert.equal(payload.likesCount, 30000);
  assert.equal(payload.followingCount, 50);
  assert.equal(payload.worksCount, 88);
  assert.equal(payload.ipLocation, '上海');
  assert.equal(payload.level, 'LV3');
  assert.equal(payload.contactPhone, '13812345678');
  assert.equal(Object.prototype.hasOwnProperty.call(payload, 'contactWechat'), false);
});

test('ensureApplyAllowed requires both apply mode and include-sensitive for contact writes', () => {
  assert.throws(() => ensureApplyAllowed({ mode: 'dry-run', includeSensitive: true, saasToken: 't' }), /mode=apply/);
  assert.throws(() => ensureApplyAllowed({ mode: 'apply', includeSensitive: false, hasSensitiveContacts: true, saasToken: 't' }), /--include-sensitive/);
  assert.throws(() => ensureApplyAllowed({ mode: 'apply', includeSensitive: true, hasSensitiveContacts: false, saasToken: '' }), /SAAS token/);
  assert.equal(ensureApplyAllowed({ mode: 'apply', includeSensitive: true, hasSensitiveContacts: true, saasToken: 't' }), true);
});

test('redactTalentRecord and toCsv keep reports safe by default', () => {
  const record = normalizeTalentRecord({
    nickname: '达人A',
    douyinAccount: 'dy_a',
    contactPhone: '13812345678'
  }, { includeSensitive: true });
  const redacted = redactTalentRecord(record);
  assert.equal(redacted.contactPhone, '138****5678');

  const csv = toCsv([redacted]);
  assert.match(csv, /nickname,douyinAccount/);
  assert.match(csv, /138\*\*\*\*5678/);
  assert.doesNotMatch(csv, /13812345678/);
});
