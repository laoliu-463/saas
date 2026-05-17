/**
 * run-all-e2e.cjs — 统一 E2E 执行入口
 *
 * 按顺序执行以下脚本，收集每个的 exit code 和 report.json，
 * 最终生成汇总报告。
 *
 * 使用方式: node run-all-e2e.cjs
 * 可通过 SKIP 环境变量跳过指定脚本（逗号分隔）:
 *   SKIP=full-browser-e2e,admin-full-demo node run-all-e2e.cjs
 */

const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const QA_DIR = __dirname;
const REPO_ROOT = path.resolve(QA_DIR, '..', '..');
const OUT_DIR = path.join(REPO_ROOT, 'out');
const QA_OUT_DIR = path.join(QA_DIR, 'out');

const now = new Date();
const pad = (n) => String(n).padStart(2, '0');
const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
const MASTER_OUT = path.join(OUT_DIR, `e2e-master-${stamp}`);
fs.mkdirSync(MASTER_OUT, { recursive: true });

// 要执行的脚本列表（顺序即执行顺序）
const SCRIPTS = [
  {
    name: 'full-browser-e2e',
    file: 'full-browser-e2e.cjs',
    desc: '全系统回归（6阶段29用例）',
    reportFile: 'report.json'
  },
  {
    name: 'real-pre-douyin-frontend',
    file: 'real-pre-douyin-frontend-e2e.cjs',
    desc: '抖店集成页面（Playwright spec: 08-real-pre-douyin-integration.spec.ts）',
    reportFile: null  // thin wrapper → playwright report 在 tests/e2e/reports/
  },
  {
    name: 'data-platform',
    file: 'data-platform-visible.cjs',
    desc: '数据平台综合（3阶段9用例）',
    reportFile: 'results.json'
  },
  {
    name: 'admin-full-demo',
    file: 'admin-full-demo.cjs',
    desc: '管理员全流程演示（A00-A16）',
    reportFile: 'report.json'
  }
];

function getSkipSet() {
  const skip = process.env.SKIP || '';
  return new Set(skip.split(',').map((s) => s.trim()).filter(Boolean));
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 在实际输出目录中查找最新生成的报告文件。
 * 各脚本的 OUT_DIR 命名规则:
 *   full-browser-e2e        → runtime/qa/out/e2e-YYYYMMDD-HHMM
 *   real-pre-douyin-frontend → playwright spec，无本地 report.json
 *   data-platform-visible     → out/e2e-data-platform-visible-*
 *   admin-full-demo           → runtime/qa/out/admin-full-demo-*
 *
 * 报告文件名: report.json 或 results.json
 */
function findLatestReport(scriptName, reportFile) {
  if (!reportFile) return null;

  const lookup = {
    'full-browser-e2e': {
      root: QA_OUT_DIR,
      match: (name) => /^e2e-\d{8}-\d{4}$/.test(name)
    },
    'data-platform': {
      root: OUT_DIR,
      match: (name) => name.startsWith('e2e-data-platform-visible-')
    },
    'admin-full-demo': {
      root: QA_OUT_DIR,
      match: (name) => name.startsWith('admin-full-demo-')
    }
  };

  const rule = lookup[scriptName];
  if (!rule) return null;

  let latestDir = null;
  let latestMtime = 0;

  let entries;
  try {
    entries = fs.readdirSync(rule.root, { withFileTypes: true });
  } catch (_) {
    return null;
  }

  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    if (!rule.match(entry.name)) continue;
    const fullPath = path.join(rule.root, entry.name);
    const stat = fs.statSync(fullPath);
    if (stat.mtimeMs > latestMtime) {
      latestMtime = stat.mtimeMs;
      latestDir = entry.name;
    }
  }

  if (!latestDir) return null;

  // 支持 report.json 或 results.json
  const candidates = ['report.json', 'results.json'];
  for (const cf of candidates) {
    const filePath = path.join(rule.root, latestDir, cf);
    if (fs.existsSync(filePath)) {
      return filePath;
    }
  }
  return null;
}

async function runScript(script) {
  const scriptPath = path.join(QA_DIR, script.file);
  if (!fs.existsSync(scriptPath)) {
    return { name: script.name, desc: script.desc, status: 'SKIP (file not found)', exitCode: -1, results: null, reportPath: null };
  }

  return new Promise((resolve) => {
    const start = Date.now();
    console.log(`\n========================================`);
    console.log(`[${new Date().toLocaleTimeString('zh-CN')}] 开始: ${script.name}`);
    console.log(`========================================`);

    const child = spawn('node', [scriptPath], {
      cwd: QA_DIR,
      stdio: 'inherit',
      shell: true,
      env: { ...process.env }
    });

    child.on('close', (code) => {
      const elapsed = Math.round((Date.now() - start) / 1000);
      console.log(`\n[${new Date().toLocaleTimeString('zh-CN')}] 完成: ${script.name} (exit ${code}, ${elapsed}s)`);

      let results = null;
      let reportPath = null;

      const found = findLatestReport(script.name, script.reportFile);
      if (found) {
        try {
          results = JSON.parse(fs.readFileSync(found, 'utf8'));
          reportPath = found;
        } catch (_) {}
      }

      resolve({
        name: script.name,
        desc: script.desc,
        status: code === 0 ? 'PASS' : 'FAIL',
        exitCode: code,
        elapsed,
        results,
        reportPath
      });
    });

    child.on('error', (err) => {
      console.error(`\n错误: ${script.name} 启动失败: ${err.message}`);
      resolve({
        name: script.name,
        desc: script.desc,
        status: 'ERROR',
        exitCode: -1,
        elapsed: 0,
        results: null,
        reportPath: null
      });
    });
  });
}

function extractResults(scriptResults) {
  // 从不同脚本的 report 结构中提取通过数/总数
  const results = [];

  for (const r of scriptResults) {
    if (!r.results) {
      results.push({ name: r.name, desc: r.desc, passed: '?', total: '?', status: r.status });
      continue;
    }

    let passed = '?';
    let total = '?';
    const phaseGroups = Array.isArray(r.results.phases)
      ? r.results.phases
      : r.results.report?.phases && typeof r.results.report.phases === 'object'
        ? Object.entries(r.results.report.phases).map(([id, cases]) => ({ id, cases: Array.isArray(cases) ? cases : [] }))
        : null;
    const cases = Array.isArray(r.results.cases)
      ? r.results.cases
      : Array.isArray(r.results.report?.cases)
        ? r.results.report.cases
        : null;

    if (cases) {
      passed = cases.filter((c) => c.result === '✅' || c.result === 'PASS').length;
      total = cases.length;
    } else if (phaseGroups) {
      const allCases = phaseGroups.flatMap((p) => p.cases || []);
      passed = allCases.filter((c) => c.result === '✅').length;
      total = allCases.length;
    } else if (Array.isArray(r.results.suite?.cases)) {
      passed = r.results.suite.cases.filter((c) => c.ok).length;
      total = r.results.suite.cases.length;
    } else if (typeof r.results.passed === 'number' && typeof r.results.total === 'number') {
      passed = r.results.passed;
      total = r.results.total;
    } else if (r.results.apiCalls) {
      // real-pre-douyin-frontend-e2e 结构
      passed = r.results.apiCalls.filter((c) => c.status >= 200 && c.status < 400 && !c.error).length;
      total = r.results.apiCalls.length;
    }

    results.push({ name: r.name, desc: r.desc, passed, total, status: r.status, elapsed: r.elapsed });
  }

  return results;
}

function generateMasterReport(results, stamp) {
  const lines = [];
  lines.push(`# E2E 统一执行报告 - ${new Date().toLocaleString('zh-CN', { hour12: false })}`);
  lines.push('');
  lines.push(`- 执行批次: ${stamp}`);
  lines.push(`- 节点: ${require('node:os').hostname()}`);
  lines.push(`- 运行时长: ${results.reduce((s, r) => s + (r.elapsed || 0), 0)}s`);
  lines.push('');
  lines.push('## 脚本汇总');
  lines.push('');
  lines.push('| 脚本 | 描述 | 状态 | 用例 | 耗时 |');
  lines.push('| --- | --- | --- | --- | --- |');

  let totalPassed = 0;
  let totalCases = 0;
  let totalElapsed = 0;

  for (const r of results) {
    const statusIcon = r.status === 'PASS' ? '✅' : r.status === 'FAIL' ? '❌' : r.status === 'SKIP (file not found)' ? '⏭️' : '⚠️';
    const caseStr = r.passed !== '?' ? `${r.passed}/${r.total}` : `${r.status.includes('SKIP') ? '跳过' : '?'}`;
    const elapsed = r.elapsed ? `${r.elapsed}s` : '-';
    lines.push(`| ${r.name} | ${r.desc} | ${statusIcon} | ${caseStr} | ${elapsed} |`);

    if (r.passed !== '?' && r.total !== '?') {
      totalPassed += Number(r.passed);
      totalCases += Number(r.total);
    }
    totalElapsed += r.elapsed || 0;
  }

  const overallIcon = totalCases > 0 && totalPassed === totalCases ? '✅' : totalCases > 0 ? '⚠️' : '❌';
  lines.push(`| **合计** | | ${overallIcon} | **${totalPassed}/${totalCases}** | **${totalElapsed}s** |`);
  lines.push('');

  // 详细脚本报告引用
  lines.push('## 脚本详细报告');
  lines.push('');
  for (const r of results) {
    const icon = r.status === 'PASS' ? '✅' : r.status === 'FAIL' ? '❌' : r.status === 'SKIP (file not found)' ? '⏭️' : '⚠️';
    lines.push(`### ${icon} ${r.name}: ${r.desc}`);
    lines.push(`- 状态: ${r.status}`);
    lines.push(`- 退出码: ${r.exitCode}`);
    lines.push(`- 耗时: ${r.elapsed}s`);
    if (r.reportPath) {
      lines.push(`- 报告: ${r.reportPath}`);
    }
    if (r.results) {
      // 打印各 phase / suite 摘要
      const phaseGroups = Array.isArray(r.results.phases)
        ? r.results.phases
        : r.results.report?.phases && typeof r.results.report.phases === 'object'
          ? Object.entries(r.results.report.phases).map(([id, cases]) => ({ id, cases: Array.isArray(cases) ? cases : [] }))
          : null;
      const cases = Array.isArray(r.results.cases)
        ? r.results.cases
        : Array.isArray(r.results.report?.cases)
          ? r.results.report.cases
          : null;
      if (phaseGroups) {
        for (const phase of phaseGroups) {
          const pPassed = (phase.cases || []).filter((c) => c.result === '✅').length;
          const pTotal = (phase.cases || []).length;
          lines.push(`  - Phase ${phase.id}${phase.title ? ` ${phase.title}` : ''}: ${pPassed}/${pTotal}`);
        }
      } else if (cases) {
        lines.push(`  - 用例: ${cases.filter((c) => c.result === '✅' || c.result === 'PASS').length}/${cases.length}`);
      } else if (r.results.apiCalls) {
        lines.push(`  - API 调用: ${r.results.apiCalls.filter((c) => c.status >= 200 && c.status < 400 && !c.error).length}/${r.results.apiCalls.length}`);
      }
    }
    lines.push('');
  }

  return lines.join('\n');
}

async function main() {
  const skipSet = getSkipSet();
  console.log('E2E 统一执行入口');
  console.log(`跳过脚本: ${skipSet.size > 0 ? [...skipSet].join(', ') : '无'}`);
  console.log('');

  const toRun = SCRIPTS.filter((s) => !skipSet.has(s.name));
  console.log(`将执行 ${toRun.length}/${SCRIPTS.length} 个脚本`);
  for (const s of toRun) {
    console.log(`  - ${s.name}: ${s.desc}`);
  }
  console.log('');

  const scriptResults = [];
  for (const script of toRun) {
    const result = await runScript(script);
    scriptResults.push(result);
    // 脚本之间休息 3s
    if (script !== toRun[toRun.length - 1]) {
      console.log('3s 后执行下一个脚本...');
      await sleep(3000);
    }
  }

  // 生成汇总报告
  const detailResults = extractResults(scriptResults);
  const masterMd = generateMasterReport(scriptResults.map((r => ({ ...r, ...detailResults[scriptResults.indexOf(r)] }))), stamp);
  const masterReportPath = path.join(MASTER_OUT, 'master-report.md');
  fs.writeFileSync(masterReportPath, masterMd, 'utf8');

  console.log('\n========================================');
  console.log('全部执行完毕');
  console.log('========================================');
  console.log(masterReportPath);
  console.log('');
  console.log('汇总:');
  for (const r of detailResults) {
    const icon = r.status === 'PASS' ? '✅' : r.status === 'FAIL' ? '❌' : r.status === 'SKIP (file not found)' ? '⏭️' : '⚠️';
    console.log(`  ${icon} ${r.name}: ${r.passed !== '?' ? `${r.passed}/${r.total}` : r.status} (${r.elapsed}s)`);
  }

  // 以非零退出码退出如果有脚本失败
  const hasFailure = scriptResults.some((r) => r.status !== 'PASS' && r.status !== 'SKIP (file not found)');
  if (hasFailure) {
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error('执行器异常:', err);
  process.exitCode = 1;
});
