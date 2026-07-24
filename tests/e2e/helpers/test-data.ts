import path from 'node:path';

export type AccountKey =
  | 'admin'
  | 'bizLeader'
  | 'bizStaff'
  | 'channelLeader'
  | 'channelStaff'
  | 'ops';

export interface AccountCredential {
  username: string;
  password: string;
}

const authDir = path.join(process.cwd(), 'tests', 'e2e', '.auth');
const defaultPassword =
  process.env.E2E_DEFAULT_PASSWORD || process.env.ADMIN_PASSWORD || 'admin123';

const defaults: Record<AccountKey, AccountCredential> = {
  admin: {
    username: process.env.E2E_ADMIN_USERNAME || 'admin',
    password: process.env.E2E_ADMIN_PASSWORD || defaultPassword
  },
  bizLeader: {
    username: process.env.E2E_BIZ_LEADER_USERNAME || 'biz_leader',
    password: process.env.E2E_BIZ_LEADER_PASSWORD || defaultPassword
  },
  bizStaff: {
    username: process.env.E2E_BIZ_STAFF_USERNAME || 'biz_staff',
    password: process.env.E2E_BIZ_STAFF_PASSWORD || defaultPassword
  },
  channelLeader: {
    username: process.env.E2E_CHANNEL_LEADER_USERNAME || process.env.E2E_CHANNEL_USERNAME || 'channel_leader',
    password: process.env.E2E_CHANNEL_LEADER_PASSWORD || process.env.E2E_CHANNEL_PASSWORD || defaultPassword
  },
  channelStaff: {
    username: process.env.E2E_CHANNEL_STAFF_USERNAME || 'channel_staff',
    password: process.env.E2E_CHANNEL_STAFF_PASSWORD || defaultPassword
  },
  ops: {
    username: process.env.E2E_OPS_USERNAME || 'ops_staff',
    password: process.env.E2E_OPS_PASSWORD || defaultPassword
  }
};

export const accounts = defaults;

export const storageStates: Record<AccountKey, string> = {
  admin: path.join(authDir, 'admin.json'),
  bizLeader: path.join(authDir, 'biz-leader.json'),
  bizStaff: path.join(authDir, 'biz-staff.json'),
  channelLeader: path.join(authDir, 'channel-leader.json'),
  channelStaff: path.join(authDir, 'channel-staff.json'),
  ops: path.join(authDir, 'ops.json')
};

export const scenarioConfig = {
  productKeyword: process.env.E2E_PRODUCT_KEYWORD || '',
  activityKeyword: process.env.E2E_ACTIVITY_KEYWORD || '',
  orderKeyword: process.env.E2E_ORDER_KEYWORD || '',
  sampleKeyword: process.env.E2E_SAMPLE_KEYWORD || ''
};

export function normalizeRoute(route: string) {
  return route.startsWith('/') ? route : `/${route}`;
}
