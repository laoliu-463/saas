(function baiyingVisibleCopySnippet() {
  function cleanText(value) {
    return String(value || '').replace(/\u00a0/g, ' ').replace(/[ \t\r\n]+/g, ' ').trim();
  }

  function parseChineseCount(value) {
    const text = cleanText(value).replace(/,/g, '');
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

  function firstMatch(text, patterns) {
    for (const pattern of patterns) {
      const match = text.match(pattern);
      if (match && cleanText(match[1])) return cleanText(match[1]);
    }
    return '';
  }

  function numberNear(text, labelPattern) {
    const match = text.match(new RegExp('(?:' + labelPattern + ')\\s*[:：]?\\s*([0-9.,]+\\s*(?:万|亿|千|w|W|k|K)?)'));
    return match ? parseChineseCount(match[1]) : null;
  }

  function isVisible(node) {
    if (!(node instanceof HTMLElement)) return false;
    const style = window.getComputedStyle(node);
    if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) return false;
    const rect = node.getBoundingClientRect();
    return rect.width > 20 && rect.height > 20 && rect.bottom >= 0 && rect.right >= 0 && rect.top <= window.innerHeight * 1.5;
  }

  function hasRecordSignal(text) {
    const countPattern = '[0-9][0-9.,]*(?:\\s*(?:万|亿|千|w|W|k|K))?';
    const metricPattern = new RegExp('(?:粉丝(?:数|量)?|获赞|点赞(?:数)?|作品(?:数)?|直播观看人数|场均结算额|销量|销售额|GMV|GPM)\\s*[:：]?\\s*' + countPattern);
    const identityPattern = /(?:抖音号|抖音账号|达人\s*UID|UID|达人ID|sec[_\s-]?uid)\s*[:：]?\s*[A-Za-z0-9_.-]+/i;
    const contactPattern = /1\d{10}|(?:联系方式|联系人|手机号|手机|电话|微信)[:：\s]*[A-Za-z0-9_.@-]{3,}/i;
    return identityPattern.test(text) || contactPattern.test(text) || metricPattern.test(text);
  }

  function extractTitle(text) {
    const ignored = /(找达人|已收藏|全部达人|直播达人|短视频达人|主推类目|带货数据|粉丝数据|达人属性|合作信息|其他筛选|暂无数据|请输入)/;
    const lines = text.split(/\n|\s{2,}/).map(cleanText).filter(Boolean);
    return lines.find((line) => line.length <= 40 && !ignored.test(line) && !/[：:]/.test(line) && !/(粉丝|获赞|点赞|作品|UID|抖音号|联系方式|手机号|电话)/.test(line)) || '';
  }

  function extractRecord(node) {
    const text = cleanText(node.innerText || node.textContent || '');
    if (!hasRecordSignal(text)) return null;
    const contactPhone = firstMatch(text, [
      /(1\d{10})/,
      /(?:联系方式|联系人|手机号|手机|电话|微信)[:：\s]*([A-Za-z0-9_.@-]{3,})/i
    ]);
    return {
      nickname: extractTitle(node.innerText || text),
      douyinAccount: firstMatch(text, [
        /抖音号\s*[:：]?\s*([A-Za-z0-9_.-]+)/i,
        /抖音账号\s*[:：]?\s*([A-Za-z0-9_.-]+)/i
      ]),
      talentUid: firstMatch(text, [
        /(?:达人\s*)?UID\s*[:：]?\s*([A-Za-z0-9_.-]+)/i,
        /达人ID\s*[:：]?\s*([A-Za-z0-9_.-]+)/i
      ]),
      fansCount: numberNear(text, '粉丝(?:数|量)?'),
      likeCount: numberNear(text, '获赞|点赞(?:数)?'),
      worksCount: numberNear(text, '作品(?:数)?'),
      ipLocation: firstMatch(text, [/(?:IP|地区|所在地)\s*[:：]\s*([^,，|;；\s]+)/i]),
      mainCategory: firstMatch(text, [/(?:主营类目|类目|行业)\s*[:：]\s*([^,，|;；\s]+)/i]),
      contactPhone,
      sourceUrl: location.href
    };
  }

  function identityKey(record) {
    return cleanText(record.talentUid || record.douyinAccount || record.nickname).toLowerCase();
  }

  function collectVisibleRecords() {
    const selectors = [
      '[class*="talent"]',
      '[class*="author"]',
      '[class*="daren"]',
      '[data-testid*="talent"]',
      '[data-testid*="author"]',
      'section',
      'article',
      'li',
      'tr'
    ];
    const candidates = Array.from(document.querySelectorAll(selectors.join(',')))
      .filter(isVisible)
      .filter((node) => cleanText(node.innerText || node.textContent || '').length >= 8)
      .filter((node) => cleanText(node.innerText || node.textContent || '').length <= 3000);
    const records = [];
    const seen = new Set();
    for (const node of candidates) {
      const record = extractRecord(node);
      if (!record) continue;
      const hasSignal = record.douyinAccount || record.talentUid || record.contactPhone
        || Number.isFinite(record.fansCount) || Number.isFinite(record.likeCount) || Number.isFinite(record.worksCount);
      const key = identityKey(record);
      if (!hasSignal || !key || seen.has(key)) continue;
      seen.add(key);
      records.push(record);
    }
    return records;
  }

  async function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return 'clipboard';
    }
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    const ok = document.execCommand('copy');
    textarea.remove();
    return ok ? 'clipboard' : 'console';
  }

  (async function run() {
    const records = collectVisibleRecords();
    const text = JSON.stringify(records, null, 2);
    window.__baiyingVisibleTalents = records;
    window.__baiyingVisibleTalentsJson = text;
    console.log('[baiying-visible-copy] records:', records);
    console.log('[baiying-visible-copy] json:', text);
    let target = 'console';
    try {
      target = await copyText(text);
    } catch {
      target = 'console';
    }
    alert('已识别 ' + records.length + ' 条当前可见达人数据，输出到 ' + (target === 'clipboard' ? '剪贴板' : 'Console 变量 window.__baiyingVisibleTalentsJson') + '。');
  })();
})();
