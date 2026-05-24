import { ROLE_CODES } from '../../constants/rbac';

const SAMPLE_EXPORT_ROLES = new Set<string>([
  ROLE_CODES.ADMIN,
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.OPS_STAFF
]);

export type SampleTabOption = { label: string; value: string };

/** 运营发货台仅处理审核通过后的物流履约，不含审核阶段状态。 */
export const OPS_HIDDEN_SAMPLE_STATUSES = new Set(['PENDING_AUDIT', 'REJECTED']);

export const OPS_SHIPPING_TABS: SampleTabOption[] = [
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已关闭', value: 'CLOSED' }
];

export const filterSampleTabsForOps = (tabs: SampleTabOption[]) =>
  tabs.filter((tab) => !OPS_HIDDEN_SAMPLE_STATUSES.has(tab.value));

export const canExportSamplesByRole = (roles: string[] = []) =>
  roles.some((role) => SAMPLE_EXPORT_ROLES.has(role));
