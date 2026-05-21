import fs from 'node:fs';
import path from 'node:path';

export function readFixture<T = unknown>(domain: string, filename: string): T {
  const fixturePath = path.join(process.cwd(), 'tests', 'e2e', 'fixtures', domain, filename);
  return JSON.parse(fs.readFileSync(fixturePath, 'utf8')) as T;
}
