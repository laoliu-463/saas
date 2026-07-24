import { execFileSync } from 'node:child_process';

interface RealPreDbOptions {
  container?: string;
  user?: string;
  database?: string;
}

const SAFE_ARG = /^[A-Za-z0-9_.-]+$/;

/**
 * 执行只读 real-pre SQL 探针。
 *
 * 默认使用本地 Docker；远端验收设置 E2E_SSH_TARGET 后，通过 SSH 进入测试服务器
 * 的 PostgreSQL 容器。SQL 从 stdin 传入，避免远端 shell 重新解释查询内容。
 */
export function runRealPreSql(sql: string, options: RealPreDbOptions = {}): string {
  const container = options.container || process.env.E2E_DB_CONTAINER || 'saas-active-postgres-real-pre-1';
  const user = options.user || process.env.E2E_DB_USER || 'saas';
  const database = options.database || process.env.E2E_DB_NAME || 'saas_real_pre';
  for (const value of [container, user, database]) {
    if (!SAFE_ARG.test(value)) {
      throw new Error(`real-pre DB 参数包含非法字符: ${value}`);
    }
  }

  const psqlArgs = [
    'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1',
    '-U', user, '-d', database, '-t', '-A', '-F', '|', '-f', '-'
  ];
  const target = String(process.env.E2E_SSH_TARGET || '').trim();
  if (target) {
    if (!SAFE_ARG.test(target)) {
      throw new Error(`E2E_SSH_TARGET 包含非法字符: ${target}`);
    }
    const remoteCommand = [
      'docker', 'exec', '-i', container,
      'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1',
      '-U', user, '-d', database, '-t', '-A', "-F '|'", '-f', '-'
    ].join(' ');
    return execFileSync('ssh.exe', [target, remoteCommand], {
      input: sql,
      encoding: 'utf8'
    });
  }

  return execFileSync('docker', ['exec', '-i', container, ...psqlArgs], {
    input: sql,
    encoding: 'utf8'
  });
}
