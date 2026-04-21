const fs = require('fs');
const xml = fs.readFileSync('target/site/jacoco/jacoco.xml', 'utf8');
const packages = [...xml.matchAll(/<package name="([^"]+)">/g)].map(m => m[1]);
const results = [];
for (const pkg of packages) {
  const start = xml.indexOf('<package name="' + pkg + '"');
  const end = xml.indexOf('</package>', start) + '</package>'.length;
  const block = xml.substring(start, end);
  const counters = [...block.matchAll(/<counter type="LINE"[^>]+>/g)];
  const last = counters[counters.length - 1];
  if (!last) { results.push({ pkg, miss: 0, cov: 0, total: 0, pct: 'n/a' }); continue; }
  const ctext = last[0];
  const miss = parseInt(ctext.match(/missed="(\d+)"/)[1]);
  const cov = parseInt(ctext.match(/covered="(\d+)"/)[1]);
  results.push({ pkg, miss, cov, total: miss + cov, pct: ((cov / (miss + cov)) * 100).toFixed(1) });
}
results.sort((a, b) => b.miss - a.miss);
results.forEach(r => console.log(
  r.pct.padStart(6) + '% | ' +
  r.miss.toString().padStart(4) + ' missed | ' +
  r.cov.toString().padStart(4) + ' covered | ' +
  r.total.toString().padStart(4) + ' total | ' +
  r.pkg
));
const totals = results.reduce((a, b) => [a[0] + b[0], a[1] + b[1]], [0, 0]);
console.log('OVERALL: ' + ((totals[1] / (totals[0] + totals[1])) * 100).toFixed(1) + '% (' + totals[1] + '/' + (totals[0] + totals[1]) + ')');