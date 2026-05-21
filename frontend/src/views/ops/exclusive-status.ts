type StatusTagType = 'success' | 'warning' | 'default';

export type ExclusiveStatusView = {
  type: StatusTagType;
  label: string;
};

const EXPIRY_KEYS = [
  'expiresAt',
  'expireAt',
  'validUntil',
  'exclusiveUntil',
  'endTime',
  'expireTime',
  'validTo',
  'effectiveEndTime'
];

const WARNING_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;

const parseDate = (value: unknown): Date | null => {
  if (!value) return null;
  const normalized = typeof value === 'string' ? value.replace(' ', 'T') : value;
  const date = new Date(normalized as any);
  return Number.isNaN(date.getTime()) ? null : date;
};

const resolveExpiryDate = (row: Record<string, unknown>): Date | null => {
  for (const key of EXPIRY_KEYS) {
    const parsed = parseDate(row[key]);
    if (parsed) return parsed;
  }
  return null;
};

const isActiveStatus = (status: unknown): boolean => {
  if (status === true) return true;
  if (typeof status === 'number') return status === 1;
  const normalized = String(status || '').trim().toUpperCase();
  return ['1', 'ACTIVE', 'VALID', 'ENABLED', '生效中'].includes(normalized);
};

export function resolveExclusiveStatusView(row: Record<string, unknown>, now = new Date()): ExclusiveStatusView {
  const expiryDate = resolveExpiryDate(row);
  const active = isActiveStatus(row.status ?? row.active);

  if (!active) {
    return { type: 'default', label: '已过期' };
  }

  if (expiryDate) {
    const msToExpiry = expiryDate.getTime() - now.getTime();
    if (msToExpiry < 0) {
      return { type: 'default', label: '已过期' };
    }
    if (msToExpiry <= WARNING_WINDOW_MS) {
      return { type: 'warning', label: '即将失效' };
    }
  }

  return { type: 'success', label: '生效中' };
}
