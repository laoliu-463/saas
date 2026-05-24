const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const readline = require('node:readline/promises');

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const {
  createEvidenceDir,
  formatLocalTimestamp,
  isRealPreRuntime,
  normalizeSystemEnv,
  resolveRealPreUrls,
  stripTrailingSlash,
  writeJson,
  writeText
} = require('./real-pre-env.cjs');

const DEFAULT_BAIYING_URL = 'https://buyin.jinritemai.com';
const DEFAULT_MODE = 'manual-import';
const DEFAULT_MAX_RECORDS = 1;
const DEFAULT_DELAY_MS = 20000;
const DEFAULT_SCROLL_PAGES = 1;
const DEFAULT_INITIAL_DWELL_MS = 30000;
const MIN_BAIYING_DELAY_MS = 15000;
const MAX_BAIYING_DELAY_MS = 120000;
const BAIYING_BROWSER_AUTOMATION_DISABLED_MESSAGE = 'Baiying direct browser automation is disabled by default. For local prototype capture only, pass --enable-browser-capture with capture or dry-run; otherwise use ordinary Chrome manually, run runtime/qa/baiying-visible-copy-snippet.js in DevTools Console, then run manual-import with --input-json or --input-html.';
const API_DIAGNOSTIC_URL_PATTERN = /seekAuthor|authorStatData|kolSquare|square_pc_api|contact_rights_info/i;
const DEFAULT_USER_DATA_DIR = path.join(
  process.env.LOCALAPPDATA || process.env.APPDATA || process.env.USERPROFILE || os.homedir(),
  'ColonelSaas',
  'baiying-playwright-profile'
);
const REPORT_COLUMNS = [
  'nickname',
  'douyinAccount',
  'talentUid',
  'secUid',
  'profileUrl',
  'avatarUrl',
  'fansCount',
  'likeCount',
  'followingCount',
  'worksCount',
  'ipLocation',
  'mainCategory',
  'talentLevel',
  'sales30d',
  'contactPhone',
  'sourceUrl',
  'missingReason'
];

function parseArgs(argv = process.argv.slice(2), env = process.env) {
  const args = {
    mode: env.BAIYING_TALENT_MODE || DEFAULT_MODE,
    configPath: env.BAIYING_TALENT_CONFIG || '',
    outputDir: env.BAIYING_TALENT_OUTPUT_DIR || '',
    inputHtml: env.BAIYING_TALENT_INPUT_HTML || '',
    inputJson: env.BAIYING_TALENT_INPUT_JSON || '',
    enableBrowserCapture: stringToBoolean(env.BAIYING_ENABLE_BROWSER_CAPTURE, false),
    userDataDir: env.BAIYING_USER_DATA_DIR || DEFAULT_USER_DATA_DIR,
    storageStatePath: env.BAIYING_STORAGE_STATE || '',
    saveStorageStatePath: env.BAIYING_SAVE_STORAGE_STATE || '',
    browserChannel: normalizeBrowserChannel(env.BAIYING_BROWSER_CHANNEL || ''),
    proxyServer: env.BAIYING_PROXY_SERVER || env.BAIYING_PROXY || '',
    proxyUsername: env.BAIYING_PROXY_USERNAME || '',
    proxyPassword: env.BAIYING_PROXY_PASSWORD || '',
    baiyingUrl: env.BAIYING_TALENT_URL || env.BAIYING_URL || DEFAULT_BAIYING_URL,
    searchKeyword: env.BAIYING_TALENT_SEARCH_KEYWORD || env.BAIYING_SEARCH_KEYWORD || '',
    clearSearch: stringToBoolean(env.BAIYING_CLEAR_SEARCH, false),
    navigationClicks: env.BAIYING_TALENT_NAVIGATION_CLICKS ? splitList(env.BAIYING_TALENT_NAVIGATION_CLICKS) : null,
    disableNavigationClicks: stringToBoolean(env.BAIYING_DISABLE_NAVIGATION_CLICKS, false),
    filterClicks: splitList(env.BAIYING_TALENT_FILTER_CLICKS || env.BAIYING_FILTER_CLICKS || ''),
    maxRecords: Number(env.BAIYING_TALENT_MAX || DEFAULT_MAX_RECORDS),
    delayMs: Number(env.BAIYING_TALENT_DELAY_MS || DEFAULT_DELAY_MS),
    initialDwellMs: Number(env.BAIYING_TALENT_INITIAL_DWELL_MS || env.BAIYING_INITIAL_DWELL_MS || DEFAULT_INITIAL_DWELL_MS),
    scrollPages: Number(env.BAIYING_TALENT_SCROLL_PAGES || DEFAULT_SCROLL_PAGES),
    manualReady: stringToBoolean(env.BAIYING_MANUAL_READY, false),
    headless: stringToBoolean(env.BAIYING_HEADLESS, false),
    includeSensitive: stringToBoolean(env.BAIYING_INCLUDE_SENSITIVE, false),
    writeSensitiveReport: stringToBoolean(env.BAIYING_WRITE_SENSITIVE_REPORT, false),
    allowNonRealPre: stringToBoolean(env.BAIYING_ALLOW_NON_REAL_PRE, false),
    saasApiBaseUrl: stripTrailingSlash(env.SAAS_API_BASE_URL || resolveRealPreUrls(env).apiBaseUrl),
    saasToken: env.SAAS_ACCESS_TOKEN || env.SAAS_TOKEN || '',
    slowMo: Number(env.BAIYING_SLOW_MO || 0)
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (['capture', 'dry-run', 'apply', 'manual-import'].includes(arg)) {
      args.mode = arg;
    } else if (arg === '--mode') {
      args.mode = argv[++i];
    } else if (arg.startsWith('--mode=')) {
      args.mode = arg.slice('--mode='.length);
    } else if (arg === '--config') {
      args.configPath = argv[++i];
    } else if (arg.startsWith('--config=')) {
      args.configPath = arg.slice('--config='.length);
    } else if (arg === '--output-dir') {
      args.outputDir = argv[++i];
    } else if (arg.startsWith('--output-dir=')) {
      args.outputDir = arg.slice('--output-dir='.length);
    } else if (arg === '--input-html') {
      args.inputHtml = argv[++i] || '';
    } else if (arg.startsWith('--input-html=')) {
      args.inputHtml = arg.slice('--input-html='.length);
    } else if (arg === '--input-json') {
      args.inputJson = argv[++i] || '';
    } else if (arg.startsWith('--input-json=')) {
      args.inputJson = arg.slice('--input-json='.length);
    } else if (arg === '--enable-browser-capture') {
      args.enableBrowserCapture = true;
    } else if (arg === '--user-data-dir') {
      args.userDataDir = argv[++i];
    } else if (arg.startsWith('--user-data-dir=')) {
      args.userDataDir = arg.slice('--user-data-dir='.length);
    } else if (arg === '--storage-state') {
      args.storageStatePath = argv[++i] || '';
    } else if (arg.startsWith('--storage-state=')) {
      args.storageStatePath = arg.slice('--storage-state='.length);
    } else if (arg === '--save-storage-state') {
      args.saveStorageStatePath = argv[++i] || '';
    } else if (arg.startsWith('--save-storage-state=')) {
      args.saveStorageStatePath = arg.slice('--save-storage-state='.length);
    } else if (arg === '--browser-channel') {
      args.browserChannel = normalizeBrowserChannel(argv[++i] || '');
    } else if (arg.startsWith('--browser-channel=')) {
      args.browserChannel = normalizeBrowserChannel(arg.slice('--browser-channel='.length));
    } else if (arg === '--chrome') {
      args.browserChannel = 'chrome';
    } else if (arg === '--proxy-server') {
      args.proxyServer = argv[++i] || '';
    } else if (arg.startsWith('--proxy-server=')) {
      args.proxyServer = arg.slice('--proxy-server='.length);
    } else if (arg === '--proxy-username') {
      args.proxyUsername = argv[++i] || '';
    } else if (arg.startsWith('--proxy-username=')) {
      args.proxyUsername = arg.slice('--proxy-username='.length);
    } else if (arg === '--proxy-password') {
      args.proxyPassword = argv[++i] || '';
    } else if (arg.startsWith('--proxy-password=')) {
      args.proxyPassword = arg.slice('--proxy-password='.length);
    } else if (arg === '--url') {
      args.baiyingUrl = argv[++i];
    } else if (arg.startsWith('--url=')) {
      args.baiyingUrl = arg.slice('--url='.length);
    } else if (arg === '--search-keyword') {
      args.searchKeyword = argv[++i] || '';
    } else if (arg.startsWith('--search-keyword=')) {
      args.searchKeyword = arg.slice('--search-keyword='.length);
    } else if (arg === '--clear-search') {
      args.clearSearch = true;
    } else if (arg === '--filter-click') {
      args.filterClicks.push(argv[++i] || '');
    } else if (arg.startsWith('--filter-click=')) {
      args.filterClicks.push(arg.slice('--filter-click='.length));
    } else if (arg === '--navigation-click') {
      if (!args.navigationClicks) args.navigationClicks = [];
      args.navigationClicks.push(argv[++i] || '');
    } else if (arg.startsWith('--navigation-click=')) {
      if (!args.navigationClicks) args.navigationClicks = [];
      args.navigationClicks.push(arg.slice('--navigation-click='.length));
    } else if (arg === '--no-navigation-clicks') {
      args.disableNavigationClicks = true;
    } else if (arg === '--max') {
      args.maxRecords = Number(argv[++i]);
    } else if (arg.startsWith('--max=')) {
      args.maxRecords = Number(arg.slice('--max='.length));
    } else if (arg === '--delay-ms') {
      args.delayMs = Number(argv[++i]);
    } else if (arg.startsWith('--delay-ms=')) {
      args.delayMs = Number(arg.slice('--delay-ms='.length));
    } else if (arg === '--initial-dwell-ms') {
      args.initialDwellMs = Number(argv[++i]);
    } else if (arg.startsWith('--initial-dwell-ms=')) {
      args.initialDwellMs = Number(arg.slice('--initial-dwell-ms='.length));
    } else if (arg === '--no-initial-dwell') {
      args.initialDwellMs = 0;
    } else if (arg === '--manual-ready') {
      args.manualReady = true;
    } else if (arg === '--scroll-pages') {
      args.scrollPages = Number(argv[++i]);
    } else if (arg.startsWith('--scroll-pages=')) {
      args.scrollPages = Number(arg.slice('--scroll-pages='.length));
    } else if (arg === '--include-sensitive') {
      args.includeSensitive = true;
    } else if (arg === '--write-sensitive-report') {
      args.writeSensitiveReport = true;
    } else if (arg === '--allow-non-real-pre') {
      args.allowNonRealPre = true;
    } else if (arg === '--headless') {
      args.headless = true;
    } else if (arg === '--headed') {
      args.headless = false;
    } else if (arg === '--saas-token') {
      args.saasToken = argv[++i];
    } else if (arg.startsWith('--saas-token=')) {
      args.saasToken = arg.slice('--saas-token='.length);
    } else if (arg === '--saas-api') {
      args.saasApiBaseUrl = stripTrailingSlash(argv[++i]);
    } else if (arg.startsWith('--saas-api=')) {
      args.saasApiBaseUrl = stripTrailingSlash(arg.slice('--saas-api='.length));
    } else if (arg === '--slow-mo') {
      args.slowMo = Number(argv[++i]);
    } else if (arg.startsWith('--slow-mo=')) {
      args.slowMo = Number(arg.slice('--slow-mo='.length));
    } else if (arg === '--help' || arg === '-h') {
      args.help = true;
    }
  }
  args.mode = normalizeMode(args.mode);
  args.maxRecords = positiveNumber(args.maxRecords, DEFAULT_MAX_RECORDS);
  args.delayMs = clampNumber(positiveNumber(args.delayMs, DEFAULT_DELAY_MS), MIN_BAIYING_DELAY_MS, MAX_BAIYING_DELAY_MS);
  args.initialDwellMs = normalizeDelayNumber(args.initialDwellMs, DEFAULT_INITIAL_DWELL_MS);
  args.scrollPages = positiveNumber(args.scrollPages, DEFAULT_SCROLL_PAGES);
  args.slowMo = Math.max(0, Number.isFinite(args.slowMo) ? args.slowMo : 0);
  args.searchKeyword = cleanText(args.searchKeyword);
  args.inputHtml = cleanText(args.inputHtml);
  args.inputJson = cleanText(args.inputJson);
  args.storageStatePath = cleanText(args.storageStatePath);
  args.saveStorageStatePath = cleanText(args.saveStorageStatePath);
  args.proxyServer = cleanText(args.proxyServer);
  args.proxyUsername = cleanText(args.proxyUsername);
  args.proxyPassword = cleanText(args.proxyPassword);
  args.filterClicks = asArray(args.filterClicks).map(cleanText).filter(Boolean);
  if (args.navigationClicks) {
    args.navigationClicks = asArray(args.navigationClicks).map(cleanText).filter(Boolean);
  }
  return args;
}

function normalizeMode(raw) {
  const mode = String(raw || DEFAULT_MODE).trim().toLowerCase();
  if (!['capture', 'dry-run', 'apply', 'manual-import'].includes(mode)) {
    throw new Error(`Unsupported mode: ${raw}. Use capture, dry-run, apply, or manual-import.`);
  }
  return mode;
}

function normalizeBrowserChannel(value) {
  const channel = String(value || '').trim().toLowerCase();
  if (!channel || channel === 'chromium' || channel === 'default') return '';
  const allowed = new Set(['chrome', 'chrome-beta', 'chrome-dev', 'chrome-canary', 'msedge', 'msedge-beta', 'msedge-dev', 'msedge-canary']);
  if (!allowed.has(channel)) {
    throw new Error(`Unsupported browser channel: ${value}. Use chrome, msedge, or leave it empty.`);
  }
  return channel;
}

function stringToBoolean(raw, fallback) {
  if (raw == null || raw === '') return fallback;
  return ['1', 'true', 'yes', 'y'].includes(String(raw).trim().toLowerCase());
}

function positiveNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function clampNumber(value, min, max) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return min;
  return Math.min(Math.max(parsed, min), max);
}

function normalizeDelayNumber(value, fallback) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) return fallback;
  if (parsed === 0) return 0;
  return clampNumber(parsed, MIN_BAIYING_DELAY_MS, MAX_BAIYING_DELAY_MS);
}

function splitList(value) {
  if (!value) return [];
  if (Array.isArray(value)) return value;
  return String(value).split(/[,\n|]/).map(cleanText).filter(Boolean);
}

function resolveUserDataDir(input) {
  const resolved = path.resolve(input || DEFAULT_USER_DATA_DIR);
  const repo = path.resolve(REPO_ROOT).toLowerCase();
  const lower = resolved.toLowerCase();
  if (lower === repo || lower.startsWith(`${repo}${path.sep}`)) {
    throw new Error(`Refusing to store Baiying browser profile inside repo: ${resolved}`);
  }
  fs.mkdirSync(resolved, { recursive: true });
  return resolved;
}

function loadConfig(configPath) {
  if (!configPath) return {};
  const resolved = path.resolve(configPath);
  return JSON.parse(fs.readFileSync(resolved, 'utf8'));
}

function hasManualInput(args) {
  return Boolean(cleanText(args.inputHtml) || cleanText(args.inputJson));
}

function resolveCollectionSource(args) {
  if (hasManualInput(args)) return 'manual-input';
  if (args.mode === 'apply') {
    throw new Error('apply mode requires --input-json or --input-html.');
  }
  if (args.mode === 'manual-import') {
    throw new Error('manual-import mode requires --input-html or --input-json.');
  }
  if (args.enableBrowserCapture && ['capture', 'dry-run'].includes(args.mode)) {
    return 'browser-capture';
  }
  throw new Error(BAIYING_BROWSER_AUTOMATION_DISABLED_MESSAGE);
}

function ensureManualInputOnly(args) {
  return resolveCollectionSource(args);
}

function collectFromInputFiles(args, outDir, run) {
  const records = [];
  if (args.inputHtml) {
    const htmlPath = path.resolve(args.inputHtml);
    const html = fs.readFileSync(htmlPath, 'utf8');
    const parsed = parseTalentCardsFromHtml(html, { includeSensitive: args.includeSensitive })
      .map((record) => ({
        ...record,
        sourceUrl: htmlPath,
        sourceLabel: 'baiying_manual_html_import',
        collectedAt: new Date().toISOString()
      }));
    records.push(...parsed);
    run.steps.push({ step: 'manualInputHtml', path: htmlPath, parsedCount: parsed.length });
  }
  if (args.inputJson) {
    const jsonPath = path.resolve(args.inputJson);
    const body = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
    const rawRecords = Array.isArray(body) ? body : Array.isArray(body.records) ? body.records : [];
    const parsed = rawRecords
      .map((record) => ({
        ...normalizeTalentRecord(record, { includeSensitive: args.includeSensitive }),
        sourceUrl: cleanText(record.sourceUrl) || jsonPath,
        sourceLabel: cleanText(record.sourceLabel) || 'baiying_manual_json_import',
        collectedAt: record.collectedAt || new Date().toISOString()
      }))
      .filter(hasUsableTalentSignal);
    records.push(...parsed);
    run.steps.push({ step: 'manualInputJson', path: jsonPath, parsedCount: parsed.length });
  }
  const deduped = dedupeRecords(records).slice(0, args.maxRecords);
  writeJson(path.join(outDir, 'manual-input-preview.redacted.json'), deduped.map(redactTalentRecord));
  return deduped;
}

function mergeRuntimeConfig(args, config) {
  const defaultNavigationClicks = Object.prototype.hasOwnProperty.call(config, 'navigationClicks')
    ? asArray(config.navigationClicks)
    : ['达人广场'];
  return {
    entryUrl: config.entryUrl || args.baiyingUrl || DEFAULT_BAIYING_URL,
    navigationClicks: args.disableNavigationClicks ? [] : (args.navigationClicks || defaultNavigationClicks),
    searchKeyword: args.searchKeyword || config.searchKeyword || '',
    clearSearch: Boolean(args.clearSearch || config.clearSearch),
    filterClicks: [...asArray(config.filterClicks || []), ...asArray(args.filterClicks || [])].map(cleanText).filter(Boolean),
    cardSelectors: asArray(config.cardSelectors || [
      '[class*="talent"]',
      '[class*="author"]',
      '[class*="daren"]',
      '[data-testid*="talent"]',
      '[data-testid*="author"]',
      '.talent-card'
    ]),
    maxRecords: Number(config.maxRecords || args.maxRecords),
    scrollPages: Number(config.scrollPages || args.scrollPages),
    delayMs: Number(config.delayMs || args.delayMs),
    initialDwellMs: normalizeDelayNumber(
      Object.prototype.hasOwnProperty.call(config, 'initialDwellMs') ? config.initialDwellMs : args.initialDwellMs,
      DEFAULT_INITIAL_DWELL_MS
    ),
    manualReady: Boolean(args.manualReady || config.manualReady),
    searchInputSelector: config.searchInputSelector || '',
    applySelectors: config.applySelectors || {},
    sourceLabel: config.sourceLabel || 'baiying_talent_square'
  };
}

function asArray(value) {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

function cleanText(value) {
  return String(value ?? '')
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t\r\n]+/g, ' ')
    .trim();
}

function decodeHtmlEntities(value) {
  return String(value || '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}

function stripHtml(value) {
  return cleanText(decodeHtmlEntities(String(value || '')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<\/(div|p|span|section|article|li|tr|h[1-6])>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')));
}

function parseChineseCount(value) {
  if (value == null || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? Math.round(value) : null;
  const text = String(value).replace(/,/g, '').trim();
  const match = text.match(/([0-9]+(?:\.[0-9]+)?)\s*([万亿千wWkK]?)/);
  if (!match) return null;
  const number = Number(match[1]);
  if (!Number.isFinite(number)) return null;
  const unit = match[2];
  const multiplier = unit === '亿' ? 100000000
    : unit === '万' || unit === 'w' || unit === 'W' ? 10000
    : unit === '千' || unit === 'k' || unit === 'K' ? 1000
    : 1;
  return Math.round(number * multiplier);
}

function maskContactValue(value) {
  const text = cleanText(value);
  if (!text) return '';
  const digits = text.replace(/\D/g, '');
  if (/^1\d{10}$/.test(digits)) {
    return `${digits.slice(0, 3)}****${digits.slice(7)}`;
  }
  if (text.length <= 2) return '*'.repeat(text.length);
  const visibleStart = Math.min(2, text.length - 1);
  const visibleEnd = Math.min(2, text.length - visibleStart);
  return `${text.slice(0, visibleStart)}${'*'.repeat(Math.max(2, text.length - visibleStart - visibleEnd))}${text.slice(text.length - visibleEnd)}`;
}

function redactSensitiveText(value) {
  return String(value || '')
    .replace(/1\d{10}/g, (match) => maskContactValue(match))
    .replace(/((?:微信|联系方式|电话|手机号|手机)[:：\s]*)([A-Za-z0-9_.@-]{4,})/gi, (_all, label, raw) => `${label}${maskContactValue(raw)}`);
}

function redactUrl(value) {
  try {
    const url = new URL(String(value || ''));
    for (const key of [...url.searchParams.keys()]) {
      if (/token|auth|cookie|session|ticket|code|key|secret|sign|csrf|xsrf|sid|uid/i.test(key)) {
        url.searchParams.set(key, '[redacted]');
      }
    }
    return url.toString();
  } catch {
    return redactSensitiveText(String(value || '').replace(/((?:token|auth|cookie|session|ticket|code|key|secret|sign|csrf|xsrf|sid|uid)=)([^&\s]+)/gi, '$1[redacted]'));
  }
}

function firstMatch(text, patterns) {
  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match && cleanText(match[1])) return cleanText(match[1]);
  }
  return '';
}

function numberNear(text, labelPattern) {
  const match = text.match(new RegExp(`(?:${labelPattern})\\s*[:：]?\\s*([0-9.,]+\\s*(?:万|亿|千|w|W|k|K)?)`));
  return match ? parseChineseCount(match[1]) : null;
}

function extractTitle(blockHtml, text) {
  const title = firstMatch(blockHtml, [
    /<h[1-6][^>]*>([\s\S]*?)<\/h[1-6]>/i,
    /<(?:span|div|p)[^>]*(?:class|data-testid)=["'][^"']*(?:name|nick|title)[^"']*["'][^>]*>([\s\S]*?)<\/(?:span|div|p)>/i
  ]);
  if (title) return stripHtml(title);
  const segments = text.split(/\s+/).map(cleanText).filter(Boolean);
  return segments.find((segment) => !/[：:]/.test(segment) && !/(粉丝|获赞|点赞|作品|UID|抖音号|联系方式)/i.test(segment)) || '';
}

function extractHref(blockHtml) {
  const href = firstMatch(blockHtml, [/href=["']([^"']+)["']/i]);
  if (!href) return '';
  if (/^https?:\/\//i.test(href)) return href;
  if (href.startsWith('//')) return `https:${href}`;
  if (href.startsWith('/')) return `${DEFAULT_BAIYING_URL}${href}`;
  return href;
}

function extractPhoneOrContact(text) {
  const phone = text.match(/1\d{10}/);
  if (phone) return phone[0];
  return firstMatch(text, [
    /(?:联系方式|联系人|手机号|手机|电话|微信)[:：\s]*([A-Za-z0-9_.@-]{3,})/i
  ]);
}

function parseTalentCard(blockHtml, options = {}) {
  const text = stripHtml(blockHtml);
  const raw = {
    nickname: extractTitle(blockHtml, text),
    douyinAccount: firstMatch(text, [
      /抖音号\s*[:：]?\s*([A-Za-z0-9_.-]+)/i,
      /抖音账号\s*[:：]?\s*([A-Za-z0-9_.-]+)/i
    ]),
    talentUid: firstMatch(text, [
      /(?:达人\s*)?UID\s*[:：]?\s*([A-Za-z0-9_.-]+)/i,
      /达人ID\s*[:：]?\s*([A-Za-z0-9_.-]+)/i
    ]),
    secUid: firstMatch(text, [/sec[_\s-]?uid\s*[:：]?\s*([A-Za-z0-9_.-]+)/i]),
    profileUrl: extractHref(blockHtml),
    avatarUrl: firstMatch(blockHtml, [/<img[^>]+src=["']([^"']+)["']/i]),
    fansCount: numberNear(text, '粉丝(?:数|量)?'),
    likeCount: numberNear(text, '获赞|点赞(?:数)?'),
    followingCount: numberNear(text, '关注(?:数)?'),
    worksCount: numberNear(text, '作品(?:数)?'),
    ipLocation: firstMatch(text, [/(?:IP|地区|所在地)\s*[:：]\s*([^,，|;；\s]+)/i]),
    mainCategory: firstMatch(text, [/(?:主营类目|类目|行业)\s*[:：]\s*([^,，|;；\s]+)/i]),
    talentLevel: firstMatch(text, [/(?:达人等级|等级)\s*[:：]\s*([A-Za-z0-9\u4e00-\u9fa5_-]+)/i]),
    sales30d: numberNear(text, '近\\s*30\\s*天(?:销量|销售额)|30\\s*天(?:销量|销售额)'),
    contactPhone: extractPhoneOrContact(text)
  };
  return normalizeTalentRecord(raw, options);
}

function parseTalentCardsFromHtml(html, options = {}) {
  const blocks = [];
  const sectionPattern = /<(section|article|li|div)[^>]*(?:class|data-testid|data-e2e)=["'][^"']*(?:talent|author|daren|达人)[^"']*["'][^>]*>[\s\S]*?<\/\1>/gi;
  for (const match of String(html || '').matchAll(sectionPattern)) {
    blocks.push(match[0]);
  }
  if (blocks.length === 0) {
    const text = stripHtml(html);
    if (/(粉丝|抖音号|UID|联系方式)/.test(text)) {
      blocks.push(String(html || ''));
    }
  }
  return blocks
    .map((block) => parseTalentCard(block, options))
    .filter(hasUsableTalentSignal);
}

function hasUsableTalentSignal(record) {
  if (!record) return false;
  const hasIdentity = Boolean(cleanText(record.douyinAccount || record.talentUid || record.secUid));
  const hasContact = Boolean(record.hasSensitiveContact || cleanText(record.contactPhone));
  const hasMetric = [
    record.fansCount,
    record.likeCount,
    record.followingCount,
    record.worksCount,
    record.sales30d
  ].some((value) => Number.isFinite(value));
  return hasIdentity || hasContact || hasMetric;
}

function normalizeTalentRecord(raw = {}, options = {}) {
  const includeSensitive = Boolean(options.includeSensitive);
  const contact = cleanText(raw.contactPhone || raw.contact || raw.contactWechat || '');
  const hasSensitiveContact = Boolean(contact);
  return {
    nickname: cleanText(raw.nickname),
    douyinAccount: cleanText(raw.douyinAccount || raw.douyinNo),
    talentUid: cleanText(raw.talentUid || raw.uid || raw.douyinUid),
    secUid: cleanText(raw.secUid),
    profileUrl: cleanText(raw.profileUrl || raw.sourceUrl),
    avatarUrl: cleanText(raw.avatarUrl),
    fansCount: parseChineseCount(raw.fansCount),
    likeCount: parseChineseCount(raw.likeCount || raw.likesCount),
    followingCount: parseChineseCount(raw.followingCount),
    worksCount: parseChineseCount(raw.worksCount),
    ipLocation: cleanText(raw.ipLocation),
    mainCategory: cleanText(raw.mainCategory || raw.categories),
    talentLevel: cleanText(raw.talentLevel || raw.level),
    sales30d: parseChineseCount(raw.sales30d),
    contactPhone: includeSensitive ? contact : maskContactValue(contact),
    hasSensitiveContact,
    sourceUrl: cleanText(raw.sourceUrl),
    sourceLabel: cleanText(raw.sourceLabel || 'baiying_talent_square'),
    collectedAt: raw.collectedAt || new Date().toISOString(),
    missingReason: cleanText(raw.missingReason)
  };
}

function redactTalentRecord(record) {
  return {
    ...record,
    contactPhone: maskContactValue(record.contactPhone),
    hasSensitiveContact: Boolean(record.hasSensitiveContact)
  };
}

function identityKey(record) {
  return cleanText(record.talentUid || record.secUid || record.douyinAccount || record.profileUrl || record.nickname).toLowerCase();
}

function dedupeRecords(records) {
  const map = new Map();
  for (const record of records) {
    const key = identityKey(record);
    if (!key) continue;
    if (!map.has(key)) {
      map.set(key, record);
    }
  }
  return [...map.values()];
}

function buildSaasCreatePayload(record) {
  const identity = cleanText(record.talentUid || record.douyinAccount || record.secUid || record.profileUrl);
  const payload = {
    douyinUid: identity,
    douyinNo: cleanText(record.douyinAccount || identity),
    uid: cleanText(record.talentUid || identity),
    secUid: cleanText(record.secUid) || undefined,
    profileUrl: cleanText(record.profileUrl) || undefined,
    nickname: cleanText(record.nickname) || cleanText(record.douyinAccount || record.talentUid || '百应达人'),
    fansCount: record.fansCount ?? undefined,
    level: cleanText(record.talentLevel) || undefined,
    avatarUrl: cleanText(record.avatarUrl) || undefined,
    categories: cleanText(record.mainCategory) || undefined,
    contactPhone: cleanText(record.contactPhone) || undefined,
    intro: buildSourceIntro(record)
  };
  return removeUndefined(payload);
}

function buildSaasManualFillPayload(record) {
  return removeUndefined({
    nickname: cleanText(record.nickname) || undefined,
    fansCount: record.fansCount ?? undefined,
    level: cleanText(record.talentLevel) || undefined,
    avatarUrl: cleanText(record.avatarUrl) || undefined,
    likesCount: record.likeCount ?? undefined,
    followingCount: record.followingCount ?? undefined,
    worksCount: record.worksCount ?? undefined,
    ipLocation: cleanText(record.ipLocation) || undefined,
    contactPhone: cleanText(record.contactPhone) || undefined,
    intro: buildSourceIntro(record)
  });
}

function buildSourceIntro(record) {
  const parts = [
    '百应达人广场采集',
    record.sourceLabel ? `source=${record.sourceLabel}` : '',
    record.collectedAt ? `collectedAt=${record.collectedAt}` : '',
    record.sourceUrl ? `sourceUrl=${record.sourceUrl}` : ''
  ].filter(Boolean);
  return parts.join('; ');
}

function removeUndefined(value) {
  const out = {};
  for (const [key, raw] of Object.entries(value)) {
    if (raw !== undefined && raw !== null && raw !== '') {
      out[key] = raw;
    }
  }
  return out;
}

function ensureApplyAllowed({ mode, includeSensitive, hasSensitiveContacts, saasToken }) {
  if (mode !== 'apply') {
    throw new Error('SaaS writes require mode=apply.');
  }
  if (hasSensitiveContacts && !includeSensitive) {
    throw new Error('Sensitive contact writes require --include-sensitive.');
  }
  if (!cleanText(saasToken)) {
    throw new Error('SAAS token is required for apply mode. Set SAAS_ACCESS_TOKEN or pass --saas-token.');
  }
  return true;
}

function toCsv(records) {
  const lines = [REPORT_COLUMNS.join(',')];
  for (const record of records) {
    lines.push(REPORT_COLUMNS.map((key) => csvEscape(record[key])).join(','));
  }
  return `${lines.join('\n')}\n`;
}

function csvEscape(value) {
  if (value == null) return '';
  const text = String(value);
  if (/[",\n\r]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function humanDelay(ms) {
  const base = clampNumber(ms, MIN_BAIYING_DELAY_MS, MAX_BAIYING_DELAY_MS);
  const jitter = Math.round(base * (0.3 + Math.random() * 0.7));
  await sleep(base + jitter);
}

async function clickByText(page, label, timeout = 5000) {
  const locator = page.getByText(label, { exact: false });
  await locator.first().waitFor({ state: 'attached', timeout }).catch(() => null);
  const count = Math.min(await locator.count().catch(() => 0), 30);
  for (let index = 0; index < count; index += 1) {
    const candidate = locator.nth(index);
    if (await candidate.isVisible({ timeout: 500 }).catch(() => false)) {
      await candidate.click({ timeout });
      return true;
    }
  }
  return false;
}

async function fillSearch(page, keyword, config) {
  if (keyword == null) return false;
  const candidates = [
    config.searchInputSelector,
    'input[placeholder*="达人"]',
    'input[placeholder*="抖音"]',
    'input[placeholder*="搜索"]',
    'input[type="search"]',
    'input[type="text"]'
  ].filter(Boolean);
  for (const selector of candidates) {
    const locator = page.locator(selector);
    await locator.first().waitFor({ state: 'attached', timeout: 5000 }).catch(() => null);
    const count = Math.min(await locator.count().catch(() => 0), 20);
    for (let index = 0; index < count; index += 1) {
      const candidate = locator.nth(index);
      if (!await candidate.isVisible({ timeout: 500 }).catch(() => false)) continue;
      await candidate.fill(keyword);
      await candidate.press('Enter').catch(() => null);
      await clickVisibleSearchButton(page).catch(() => null);
      return true;
    }
  }
  return false;
}

async function clickVisibleSearchButton(page) {
  const candidates = [
    '.auxo-input-search-button',
    'button:has([aria-label="search"])',
    'button:has(svg[data-icon="search"])'
  ];
  for (const selector of candidates) {
    const locator = page.locator(selector);
    const count = Math.min(await locator.count().catch(() => 0), 10);
    for (let index = 0; index < count; index += 1) {
      const candidate = locator.nth(index);
      if (!await candidate.isVisible({ timeout: 300 }).catch(() => false)) continue;
      await candidate.click({ timeout: 3000 });
      return true;
    }
  }
  return false;
}

async function waitForPageIdle(page, delayMs) {
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => null);
  await humanDelay(delayMs);
}

async function waitForTalentSquareReady(page) {
  const readySelectors = [
    '.daren-square',
    '#page-find-daren',
    'input[type="text"]'
  ];
  for (const selector of readySelectors) {
    if (await page.locator(selector).first().isVisible({ timeout: 5000 }).catch(() => false)) {
      return true;
    }
  }
  return false;
}

async function prepareBaiyingPage(page, config, run) {
  await page.goto(config.entryUrl, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await waitForPageIdle(page, config.delayMs);
  if (config.initialDwellMs > 0) {
    run.steps.push({ step: 'initialDwell', delayMs: config.initialDwellMs });
    await humanDelay(config.initialDwellMs);
  }
  if (config.manualReady) {
    await waitForManualReady(run);
  }
  await assertBaiyingLoggedIn(page);
  for (const label of config.navigationClicks) {
    const clicked = await clickByText(page, label).catch(() => false);
    run.steps.push({ step: 'navigationClick', label, clicked });
    if (clicked) await waitForPageIdle(page, config.delayMs);
  }
  await waitForTalentSquareReady(page);
  for (const label of config.filterClicks) {
    const clicked = await clickByText(page, label).catch(() => false);
    run.steps.push({ step: 'filterClick', label, clicked });
    if (clicked) await waitForPageIdle(page, config.delayMs);
  }
  if (config.clearSearch && !config.searchKeyword) {
    const cleared = await fillSearch(page, '', config).catch(() => false);
    run.steps.push({ step: 'clearSearch', cleared });
    if (cleared) await waitForPageIdle(page, config.delayMs);
  }
  if (config.searchKeyword) {
    const filled = await fillSearch(page, config.searchKeyword, config).catch(() => false);
    run.steps.push({ step: 'searchKeyword', keyword: config.searchKeyword, filled });
    if (filled) await waitForPageIdle(page, config.delayMs);
  }
}

async function waitForManualReady(run) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    await rl.question('[baiying-collect] Adjust Baiying filters manually, then press Enter to continue...');
    run.steps.push({ step: 'manualReady', confirmed: true });
  } finally {
    rl.close();
  }
}

async function assertBaiyingLoggedIn(page) {
  const loggedOut = await page.evaluate(() => {
    const text = document.body?.innerText || '';
    const controls = Array.from(document.querySelectorAll('button, a')).map((node) => (node.innerText || node.textContent || '').trim());
    return controls.some((item) => item === '登录') || /请登录|立即登录/.test(text);
  }).catch(() => false);
  if (loggedOut) {
    throw new Error('Baiying appears logged out. Use headed mode to log in, or pass a valid --storage-state file.');
  }
}

function summarizeBaiyingApiResponse(url, status, bodyText) {
  const summary = { status, url: redactUrl(url) };
  if (status >= 400) {
    return { ...summary, message: `HTTP ${status}` };
  }
  let body = null;
  try {
    body = bodyText ? JSON.parse(bodyText) : null;
  } catch {
    return null;
  }
  if (!body || typeof body !== 'object') return null;
  const code = body.code ?? body.st;
  const st = body.st;
  const msg = cleanText(body.msg || body.message || body.err_msg || body.error_msg || '');
  const okCode = code == null || code === 0 || code === 200 || code === '0' || code === '200';
  if (!okCode || /网络|请求失败|加载失败|服务异常|系统异常|稍后再试|稍后重试/.test(msg)) {
    return removeUndefined({
      ...summary,
      code,
      st,
      message: msg || `Baiying API returned code=${code}`
    });
  }
  return null;
}

function isBlockingBaiyingApiDiagnostic(item) {
  if (!item) return false;
  const code = String(item.code ?? item.st ?? '');
  const message = cleanText(item.message || '');
  return code === '9001' || /异常操作|风控|验证失败|操作过于频繁/.test(message);
}

function hasBlockingBaiyingApiDiagnostic(run) {
  return Boolean((run.apiDiagnostics || []).some(isBlockingBaiyingApiDiagnostic));
}

function buildProxyOptions(args) {
  if (!args.proxyServer) return undefined;
  return removeUndefined({
    server: args.proxyServer,
    username: args.proxyUsername,
    password: args.proxyPassword
  });
}

function proxySummary(args) {
  if (!args.proxyServer) return null;
  return removeUndefined({
    server: redactUrl(args.proxyServer),
    username: args.proxyUsername ? '[set]' : undefined,
    password: args.proxyPassword ? '[set]' : undefined
  });
}

function ensureParentDir(filePath) {
  fs.mkdirSync(path.dirname(path.resolve(filePath)), { recursive: true });
}

async function collectFromBrowserSession(config, args, outDir, run) {
  let chromium;
  try {
    ({ chromium } = require('playwright'));
  } catch (error) {
    throw new Error(`Playwright package is required for --enable-browser-capture. Run npm install first. ${error.message}`);
  }

  const proxy = buildProxyOptions(args);
  const launchOptions = removeUndefined({
    headless: args.headless,
    slowMo: args.slowMo,
    channel: args.browserChannel || undefined
  });
  const contextOptions = removeUndefined({
    viewport: { width: 1440, height: 900 },
    proxy
  });
  let browser = null;
  let context = null;
  let userDataDir = '';

  try {
    if (args.storageStatePath) {
      browser = await chromium.launch(launchOptions);
      context = await browser.newContext({
        ...contextOptions,
        storageState: path.resolve(args.storageStatePath)
      });
    } else {
      userDataDir = resolveUserDataDir(args.userDataDir);
      context = await chromium.launchPersistentContext(userDataDir, {
        ...launchOptions,
        ...contextOptions
      });
    }

    run.collectionSource = 'browser-capture';
    run.steps.push({
      step: 'browserCaptureStart',
      mode: args.mode,
      entryUrl: redactUrl(config.entryUrl),
      userDataDir: userDataDir || undefined,
      storageStatePath: args.storageStatePath ? path.resolve(args.storageStatePath) : undefined,
      saveStorageStatePath: args.saveStorageStatePath ? path.resolve(args.saveStorageStatePath) : undefined,
      browserChannel: args.browserChannel || 'bundled-chromium',
      headless: args.headless,
      proxy: proxySummary(args)
    });

    const page = context.pages()[0] || await context.newPage();
    attachBaiyingApiDiagnostics(page, run);
    const records = await collectFromBrowser(page, config, args, outDir, run);

    if (args.saveStorageStatePath) {
      const storagePath = path.resolve(args.saveStorageStatePath);
      ensureParentDir(storagePath);
      await context.storageState({ path: storagePath });
      run.steps.push({ step: 'saveStorageState', path: storagePath });
    }

    return records;
  } finally {
    if (context) await context.close().catch(() => null);
    if (browser) await browser.close().catch(() => null);
  }
}

function attachBaiyingApiDiagnostics(page, run) {
  page.on('requestfailed', (request) => {
    const url = request.url();
    if (!API_DIAGNOSTIC_URL_PATTERN.test(url)) return;
    pushApiDiagnostic(run, {
      type: 'requestfailed',
      method: request.method(),
      url: redactUrl(url),
      message: request.failure()?.errorText || 'request failed'
    });
  });
  page.on('response', async (response) => {
    const url = response.url();
    if (!API_DIAGNOSTIC_URL_PATTERN.test(url)) return;
    let bodyText = '';
    try {
      bodyText = await response.text();
    } catch (error) {
      if (response.status() >= 400) {
        pushApiDiagnostic(run, {
          type: 'response',
          status: response.status(),
          url: redactUrl(url),
          message: error.message
        });
      }
      return;
    }
    const summary = summarizeBaiyingApiResponse(url, response.status(), bodyText);
    if (summary) {
      pushApiDiagnostic(run, { type: 'response', ...summary });
    }
  });
}

function pushApiDiagnostic(run, item) {
  if (!run.apiDiagnostics) run.apiDiagnostics = [];
  if (run.apiDiagnostics.length >= 20) return;
  run.apiDiagnostics.push(item);
}

async function extractBlocksFromPage(page, config) {
  return page.evaluate((input) => {
    const selectors = input.cardSelectors || [];
    const seen = new Set();
    const nodes = [];
    const countPattern = '[0-9][0-9.,]*(?:\\s*(?:万|亿|千|w|W|k|K))?';
    const metricPattern = new RegExp(`(?:粉丝(?:数|量)?|获赞|点赞(?:数)?|作品(?:数)?|直播观看人数|场均结算额|销量|销售额|GMV|GPM)\\s*[:：]?\\s*${countPattern}`);
    const identityPattern = /(?:抖音号|抖音账号|达人\s*UID|UID|达人ID|sec[_\s-]?uid)\s*[:：]?\s*[A-Za-z0-9_.-]+/i;
    const contactPattern = /1\d{10}|(?:联系方式|联系人|手机号|手机|电话|微信)[:：\s]*[A-Za-z0-9_.@-]{3,}/i;
    const hasRecordSignal = (text) => identityPattern.test(text) || contactPattern.test(text) || metricPattern.test(text);
    const pushNode = (node) => {
      const text = (node.innerText || node.textContent || '').trim();
      if (!text || text.length < 8) return;
      if (text.length > 3000) return;
      if (!hasRecordSignal(text)) return;
      const key = text.slice(0, 300);
      if (seen.has(key)) return;
      seen.add(key);
      nodes.push({
        html: node.outerHTML,
        text,
        sourceUrl: location.href
      });
    };
    for (const selector of selectors) {
      try {
        document.querySelectorAll(selector).forEach(pushNode);
      } catch {
        // Ignore invalid selectors supplied by calibration configs.
      }
    }
    if (nodes.length === 0) {
      Array.from(document.querySelectorAll('section, article, li, tr, div')).forEach((node) => {
        const text = (node.innerText || node.textContent || '').trim();
        if (!text || text.length < 20 || text.length > 2000) return;
        if (!hasRecordSignal(text)) return;
        const key = text.slice(0, 300);
        if (seen.has(key)) return;
        seen.add(key);
        nodes.push({
          html: node.outerHTML,
          text,
          sourceUrl: location.href
        });
      });
    }
    return nodes.slice(0, input.maxRecords || 5);
  }, {
    cardSelectors: config.cardSelectors,
    maxRecords: config.maxRecords
  });
}

async function collectFromBrowser(page, config, args, outDir, run) {
  await prepareBaiyingPage(page, config, run);
  const rawBlocks = [];
  const records = [];
  if (hasBlockingBaiyingApiDiagnostic(run)) {
    run.steps.push({
      step: 'stopOnBaiyingRiskControl',
      reason: 'Baiying returned a blocking abnormal-operation diagnostic.'
    });
    await writeCaptureArtifacts(page, rawBlocks, records, outDir, args);
    return records;
  }

  for (let pageIndex = 0; pageIndex < config.scrollPages && records.length < config.maxRecords; pageIndex += 1) {
    if (hasBlockingBaiyingApiDiagnostic(run)) {
      run.steps.push({
        step: 'stopOnBaiyingRiskControl',
        reason: 'Baiying returned a blocking abnormal-operation diagnostic.'
      });
      break;
    }
    const blocks = await extractBlocksFromPage(page, config);
    rawBlocks.push(...blocks);
    for (const block of blocks) {
      const parsed = parseTalentCardsFromHtml(block.html, { includeSensitive: args.includeSensitive })
        .map((record) => ({
          ...record,
          sourceUrl: block.sourceUrl,
          sourceLabel: config.sourceLabel,
          collectedAt: new Date().toISOString()
        }));
      records.push(...parsed);
    }
    await page.evaluate(() => window.scrollBy(0, Math.max(window.innerHeight, 800))).catch(() => null);
    await humanDelay(config.delayMs);
  }

  const deduped = dedupeRecords(records).slice(0, config.maxRecords);
  await writeCaptureArtifacts(page, rawBlocks, deduped, outDir, args);
  return deduped;
}

async function writeCaptureArtifacts(page, rawBlocks, records, outDir, args) {
  await page.screenshot({ path: path.join(outDir, 'baiying-page.png'), fullPage: true }).catch(() => null);
  const redactedBlocks = rawBlocks.slice(0, 5).map((block) => ({
    sourceUrl: block.sourceUrl,
    text: truncateText(redactSensitiveText(block.text), 5000),
    html: truncateText(redactSensitiveText(block.html), 12000)
  }));
  writeJson(path.join(outDir, 'capture-blocks.redacted.json'), redactedBlocks);
  writeJson(path.join(outDir, 'field-preview.redacted.json'), records.map(redactTalentRecord));
  if (args.includeSensitive && args.writeSensitiveReport) {
    writeJson(path.join(outDir, 'capture-blocks.sensitive.local.json'), rawBlocks.slice(0, 5));
    writeJson(path.join(outDir, 'field-preview.sensitive.local.json'), records);
  }
}

function truncateText(value, maxLength) {
  const text = String(value || '');
  if (text.length <= maxLength) return text;
  return `${text.slice(0, maxLength)}\n...[truncated ${text.length - maxLength} chars]`;
}

async function verifySaasEnvironment(apiBaseUrl, token, allowNonRealPre) {
  const result = await apiRequest(apiBaseUrl, '/system/env', {
    method: 'GET',
    token
  });
  const env = normalizeSystemEnv(result.body);
  if (!allowNonRealPre && !isRealPreRuntime(env)) {
    throw new Error(`Refusing apply outside REAL-PRE: ${JSON.stringify(env)}`);
  }
  return env;
}

async function apiRequest(apiBaseUrl, apiPath, options = {}) {
  const headers = {
    Accept: 'application/json',
    ...(options.data ? { 'Content-Type': 'application/json' } : {}),
    ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
  };
  const res = await fetch(`${stripTrailingSlash(apiBaseUrl)}${apiPath}`, {
    method: options.method || 'GET',
    headers,
    body: options.data ? JSON.stringify(options.data) : undefined
  });
  const text = await res.text();
  let body = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  if (!res.ok || (body && body.code && body.code !== 200)) {
    const message = body?.msg || body?.message || text || `HTTP ${res.status}`;
    const error = new Error(message);
    error.status = res.status;
    error.body = body;
    throw error;
  }
  return { status: res.status, body };
}

function unwrapData(body) {
  return body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data') ? body.data : body;
}

function extractRecords(body) {
  const data = unwrapData(body);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.records)) return data.records;
  if (Array.isArray(data?.list)) return data.list;
  return [];
}

async function findExistingTalent(apiBaseUrl, token, record) {
  const keyword = cleanText(record.talentUid || record.douyinAccount || record.nickname);
  if (!keyword) return null;
  const result = await apiRequest(apiBaseUrl, `/talents?page=1&size=10&keyword=${encodeURIComponent(keyword)}`, {
    method: 'GET',
    token
  });
  const records = extractRecords(result.body);
  const identities = new Set([
    cleanText(record.talentUid),
    cleanText(record.douyinAccount),
    cleanText(record.secUid)
  ].filter(Boolean).map((item) => item.toLowerCase()));
  return records.find((item) => {
    const values = [item.douyinUid, item.douyinNo, item.uid, item.secUid, item.nickname]
      .map(cleanText)
      .filter(Boolean)
      .map((value) => value.toLowerCase());
    return values.some((value) => identities.has(value)) || cleanText(item.nickname) === cleanText(record.nickname);
  }) || null;
}

async function applyRecordsToSaas(records, args) {
  ensureApplyAllowed({
    mode: args.mode,
    includeSensitive: args.includeSensitive,
    hasSensitiveContacts: records.some((record) => record.hasSensitiveContact),
    saasToken: args.saasToken
  });
  const env = await verifySaasEnvironment(args.saasApiBaseUrl, args.saasToken, args.allowNonRealPre);
  const results = [];
  for (const record of records) {
    const identity = cleanText(record.talentUid || record.douyinAccount || record.secUid || record.profileUrl);
    if (!identity) {
      results.push({ nickname: record.nickname, action: 'skipped', ok: false, reason: 'missing identity' });
      continue;
    }
    try {
      const existing = await findExistingTalent(args.saasApiBaseUrl, args.saasToken, record);
      if (existing?.id) {
        const payload = buildSaasManualFillPayload(record);
        await apiRequest(args.saasApiBaseUrl, `/talents/${existing.id}/manual-fill`, {
          method: 'PUT',
          token: args.saasToken,
          data: payload
        });
        results.push({ nickname: record.nickname, talentId: existing.id, action: 'updated', ok: true });
      } else {
        const payload = buildSaasCreatePayload(record);
        const created = await apiRequest(args.saasApiBaseUrl, '/talents', {
          method: 'POST',
          token: args.saasToken,
          data: payload
        });
        const createdData = unwrapData(created.body);
        if (createdData?.id) {
          try {
            await apiRequest(args.saasApiBaseUrl, `/talents/${createdData.id}/manual-fill`, {
              method: 'PUT',
              token: args.saasToken,
              data: buildSaasManualFillPayload(record)
            });
            results.push({ nickname: record.nickname, talentId: createdData.id, action: 'created_and_filled', ok: true });
          } catch (fillError) {
            results.push({
              nickname: record.nickname,
              talentId: createdData.id,
              action: 'created_manual_fill_failed',
              ok: true,
              reason: fillError.message,
              status: fillError.status
            });
          }
        } else {
          results.push({ nickname: record.nickname, talentId: createdData?.id, action: 'created', ok: true });
        }
      }
    } catch (error) {
      if (/已存在|duplicate/i.test(error.message || '')) {
        const existing = await findExistingTalent(args.saasApiBaseUrl, args.saasToken, record).catch(() => null);
        if (existing?.id) {
          await apiRequest(args.saasApiBaseUrl, `/talents/${existing.id}/manual-fill`, {
            method: 'PUT',
            token: args.saasToken,
            data: buildSaasManualFillPayload(record)
          });
          results.push({ nickname: record.nickname, talentId: existing.id, action: 'updated_after_duplicate', ok: true });
          continue;
        }
      }
      results.push({
        nickname: record.nickname,
        action: 'failed',
        ok: false,
        reason: error.message,
        status: error.status
      });
    }
    await humanDelay(args.delayMs);
  }
  return { env, results };
}

function writeRunReport(outDir, run, records, applyResult = null, args) {
  const redactedRecords = records.map(redactTalentRecord);
  const summary = {
    script: 'baiying-talent-collect',
    generatedAt: new Date().toISOString(),
    mode: args.mode,
    collectionSource: run.collectionSource || (hasManualInput(args) ? 'manual-input' : 'unknown'),
    outputDir: outDir,
    includeSensitive: args.includeSensitive,
    writeSensitiveReport: args.writeSensitiveReport,
    recordCount: records.length,
    sensitiveContactCount: records.filter((record) => record.hasSensitiveContact).length,
    records: redactedRecords,
    apply: applyResult ? {
      env: applyResult.env,
      results: applyResult.results
    } : null,
    apiDiagnostics: run.apiDiagnostics || [],
    blockedByBaiyingRiskControl: hasBlockingBaiyingApiDiagnostic(run),
    steps: run.steps
  };
  writeJson(path.join(outDir, 'summary.json'), summary);
  writeJson(path.join(outDir, 'records.redacted.json'), redactedRecords);
  writeText(path.join(outDir, 'records.redacted.csv'), toCsv(redactedRecords));
  if (args.includeSensitive && args.writeSensitiveReport) {
    writeJson(path.join(outDir, 'records.sensitive.local.json'), records);
    writeText(path.join(outDir, 'records.sensitive.local.csv'), toCsv(records));
  }
  const lines = [
    '# Baiying Talent Collect',
    '',
    `- Mode: ${args.mode}`,
    `- Generated at: ${summary.generatedAt}`,
    `- Records: ${records.length}`,
    `- Sensitive contacts observed: ${summary.sensitiveContactCount}`,
    `- Sensitive report written: ${args.includeSensitive && args.writeSensitiveReport ? 'YES' : 'NO'}`,
    '',
    '## Apply Results',
    ''
  ];
  if (applyResult) {
    for (const item of applyResult.results) {
      lines.push(`- ${item.nickname || item.talentId || 'unknown'}: ${item.ok ? 'PASS' : 'FAIL'} ${item.action}${item.reason ? ` (${item.reason})` : ''}`);
    }
  } else {
    lines.push('- Not applied.');
  }
  if (summary.apiDiagnostics.length > 0) {
    lines.push('', '## Baiying API Diagnostics', '');
    for (const item of summary.apiDiagnostics) {
      lines.push(`- ${item.status || item.type}: ${item.message || 'diagnostic'}${item.code != null ? ` (code=${item.code})` : ''}`);
    }
    if (summary.blockedByBaiyingRiskControl) {
      lines.push('', 'Baiying risk-control diagnostic detected. Collector stopped further page extraction.');
    }
  }
  writeText(path.join(outDir, 'report.md'), `${lines.join('\n')}\n`);
}

function printHelp() {
  console.log(`Usage:
  node runtime/qa/baiying-talent-collect.cjs manual-import --input-json <file> [options]
  node runtime/qa/baiying-talent-collect.cjs manual-import --input-html <file> [options]
  node runtime/qa/baiying-talent-collect.cjs capture --enable-browser-capture [options]
  node runtime/qa/baiying-talent-collect.cjs dry-run --enable-browser-capture [options]
  node runtime/qa/baiying-talent-collect.cjs apply --input-json <file> --include-sensitive --saas-token <token> [options]

Options:
  --input-html <file>         Parse a manually saved Baiying page HTML file; no browser access
  --input-json <file>         Parse manually prepared talent JSON records; no browser access
  --enable-browser-capture    Enable local Playwright prototype capture for capture/dry-run only
  --user-data-dir <dir>       Local Playwright profile dir. Default: ${DEFAULT_USER_DATA_DIR}
  --storage-state <file>      Load Playwright storageState JSON instead of persistent profile
  --save-storage-state <file> Save current auth state after local capture
  --proxy-server <url>        Optional fixed proxy, e.g. http://127.0.0.1:7890
  --proxy-username <value>    Optional proxy username
  --proxy-password <value>    Optional proxy password
  --headed / --headless       Browser visibility. Default: headed
  --manual-ready              Wait for Enter after you manually log in or adjust filters
  --max <n>                   Max records. Default: ${DEFAULT_MAX_RECORDS}
  --include-sensitive         Keep visible contact values in memory for apply
  --write-sensitive-report    Also write local sensitive JSON/CSV reports
  --saas-token <token>        SaaS access token for apply mode
  --saas-api <url>            SaaS API base. Default: http://localhost:8081/api

Notes:
  Playwright capture is a local prototype path only. It writes redacted evidence files under runtime/qa/out,
  never writes SaaS data, and stops on visible Baiying risk-control diagnostics.
  Apply mode still requires --input-json or --input-html and never crawls Baiying directly.

Examples:
  npm run qa:baiying:talents -- manual-import --input-json runtime/qa/baiying-visible-talents.json --max 20
  npm run qa:baiying:talents -- manual-import --input-html runtime/qa/out/baiying-page.html --max 20
  npm run qa:baiying:talents -- capture --enable-browser-capture --manual-ready --save-storage-state runtime/auth/douyin-storage-state.json --max 1
  npm run qa:baiying:talents -- dry-run --enable-browser-capture --storage-state runtime/auth/douyin-storage-state.json --proxy-server http://127.0.0.1:7890 --max 1
  npm run qa:baiying:talents -- apply --input-json runtime/qa/baiying-visible-talents.json --include-sensitive --saas-token <token>
`);
}

async function main() {
  const args = parseArgs();
  if (args.help) {
    printHelp();
    return;
  }
  const outDir = path.resolve(args.outputDir || createEvidenceDir(REPO_ROOT, `baiying-talent-collect-${args.mode}`));
  fs.mkdirSync(outDir, { recursive: true });
  const run = { steps: [] };
  let records = [];
  let applyResult = null;

  const collectionSource = resolveCollectionSource(args);
  run.collectionSource = collectionSource;
  if (collectionSource === 'manual-input') {
    records = collectFromInputFiles(args, outDir, run);
  } else {
    const config = mergeRuntimeConfig(args, loadConfig(args.configPath));
    records = await collectFromBrowserSession(config, args, outDir, run);
  }
  if (args.mode === 'apply') {
    applyResult = await applyRecordsToSaas(records, args);
  }
  writeRunReport(outDir, run, records, applyResult, args);

  console.log(`baiying talent collect output: ${outDir}`);
  console.log(`source: ${collectionSource}`);
  if (args.mode !== 'apply') {
    console.log('No SaaS writes were performed.');
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = {
  buildSaasCreatePayload,
  buildSaasManualFillPayload,
  cleanText,
  collectFromInputFiles,
  dedupeRecords,
  ensureApplyAllowed,
  ensureManualInputOnly,
  extractRecords,
  hasManualInput,
  hasBlockingBaiyingApiDiagnostic,
  isBlockingBaiyingApiDiagnostic,
  maskContactValue,
  hasUsableTalentSignal,
  normalizeBrowserChannel,
  normalizeTalentRecord,
  parseArgs,
  parseChineseCount,
  parseTalentCardsFromHtml,
  redactSensitiveText,
  redactTalentRecord,
  resolveCollectionSource,
  summarizeBaiyingApiResponse,
  toCsv
};
