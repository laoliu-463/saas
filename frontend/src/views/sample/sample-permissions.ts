/**
 * 寄样模块权限控制工具
 *
 * 提供基于角色的寄样数据导出权限判断，以及运营角色的 Tab 可见性过滤。
 * 配合 ROLE_CODES 常量，控制不同角色在寄样列表中可看到的状态 Tab 以及是否允许导出。
 */
import { ROLE_CODES, hasAccess, hasAnyRole } from '../../constants/rbac';

/** 允许导出寄样数据的角色集合：管理员、业务组长、业务专员、运营专员、渠道组长 */
const SAMPLE_EXPORT_ROLES = new Set<string>([
  ROLE_CODES.ADMIN,
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.OPS_STAFF,
  ROLE_CODES.CHANNEL_LEADER
]);

/** 寄样 Tab 选项的基础类型（label 显示文本 + value 状态标识） */
export type SampleTabOption = { label: string; value: string };

export const SAMPLE_STATUS = {
  PENDING_AUDIT: 'PENDING_AUDIT',
  PENDING_SHIP: 'PENDING_SHIP',
  SHIPPED: 'SHIPPED',
  SIGNED: 'SIGNED',
  PENDING_TASK: 'PENDING_TASK',
  FINISHED: 'FINISHED',
  REJECTED: 'REJECTED',
  CLOSED: 'CLOSED'
} as const;

export type SampleStatus = (typeof SAMPLE_STATUS)[keyof typeof SAMPLE_STATUS];

/** UI 状态展示来自后端状态字段；前端只负责展示与入口可见性，不作为状态机判定源。 */
export const ALL_SAMPLE_STATUS_TABS: SampleTabOption[] = [
  { label: '全部', value: '' },
  { label: '待审核', value: SAMPLE_STATUS.PENDING_AUDIT },
  { label: '待发货', value: SAMPLE_STATUS.PENDING_SHIP },
  { label: '快递中', value: SAMPLE_STATUS.SHIPPED },
  { label: '待交作业', value: SAMPLE_STATUS.PENDING_TASK },
  { label: '已完成', value: SAMPLE_STATUS.FINISHED },
  { label: '已拒绝', value: SAMPLE_STATUS.REJECTED },
  { label: '已关闭', value: SAMPLE_STATUS.CLOSED }
];

/** 运营侧发货台仅处理审核通过后的物流履约，不含审核阶段状态。 */
export const OPS_HIDDEN_SAMPLE_STATUSES = new Set<string>([
  SAMPLE_STATUS.PENDING_AUDIT,
  SAMPLE_STATUS.REJECTED
]);

/** 运营角色发货台可用的寄样状态 Tab 列表 */
export const OPS_SHIPPING_TABS: SampleTabOption[] = [
  { label: '待发货', value: SAMPLE_STATUS.PENDING_SHIP },
  { label: '快递中', value: SAMPLE_STATUS.SHIPPED },
  { label: '待交作业', value: SAMPLE_STATUS.PENDING_TASK },
  { label: '已完成', value: SAMPLE_STATUS.FINISHED },
  { label: '已关闭', value: SAMPLE_STATUS.CLOSED }
];

export const SAMPLE_FLOW_STATUS_ORDER: SampleStatus[] = [
  SAMPLE_STATUS.PENDING_AUDIT,
  SAMPLE_STATUS.PENDING_SHIP,
  SAMPLE_STATUS.SHIPPED,
  SAMPLE_STATUS.PENDING_TASK,
  SAMPLE_STATUS.FINISHED
];

export const SAMPLE_STATUS_LABELS: Record<string, string> = {
  [SAMPLE_STATUS.PENDING_AUDIT]: '待审核',
  [SAMPLE_STATUS.PENDING_SHIP]: '待发货',
  [SAMPLE_STATUS.SHIPPED]: '快递中',
  [SAMPLE_STATUS.SIGNED]: '已签收',
  [SAMPLE_STATUS.PENDING_TASK]: '待交作业',
  [SAMPLE_STATUS.FINISHED]: '已完成',
  [SAMPLE_STATUS.REJECTED]: '已拒绝',
  [SAMPLE_STATUS.CLOSED]: '已关闭'
};

/**
 * 按运营角色过滤寄样列表的 Tab 选项
 *
 * 移除运营不可见的状态（待审核、已拒绝），确保运营只关注需要执行发货操作的寄样单。
 *
 * @param tabs - 当前可用的寄样状态 Tab 列表
 * @returns 过滤后的 Tab 列表
 */
export const filterSampleTabsForOps = (tabs: SampleTabOption[]) =>
  tabs.filter((tab) => !OPS_HIDDEN_SAMPLE_STATUSES.has(tab.value));

/**
 * 判断当前用户角色是否具有寄样数据导出权限
 *
 * 允许导出的角色：管理员、业务组长、业务专员、运营专员、渠道组长。
 *
 * @param roles - 当前用户的角色编码数组
 * @returns 若拥有导出权限返回 true，否则返回 false
 */
export const canExportSamplesByRole = (roles: string[] = []) =>
  hasAccess(roles, Array.from(SAMPLE_EXPORT_ROLES));

export const canApplySampleByRole = (roles: readonly string[] = []) =>
  hasAnyRole(roles, [ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF]);

export const canAuditSamplesByRole = (roles: readonly string[] = [], isAdmin = false) =>
  isAdmin || hasAccess(roles, [ROLE_CODES.BIZ_STAFF]);

export const canShipSamplesByRole = (roles: readonly string[] = [], isAdmin = false) =>
  isAdmin || hasAccess(roles, [ROLE_CODES.OPS_STAFF]);

export const canSubmitSampleByRole = (roles: readonly string[] = []) =>
  !hasAnyRole(roles, ['visitor', 'readonly']);

export const isOpsStaffOnlyRole = (roles: readonly string[] = [], isAdmin = false) =>
  hasAnyRole(roles, [ROLE_CODES.OPS_STAFF]) && !isAdmin;

export const isBizStaffOnlyRole = (roles: readonly string[] = [], isAdmin = false) =>
  hasAnyRole(roles, [ROLE_CODES.BIZ_STAFF])
  && !hasAnyRole(roles, [ROLE_CODES.BIZ_LEADER])
  && !isAdmin;

export const isChannelStaffOnlyRole = (roles: readonly string[] = [], isAdmin = false) =>
  hasAnyRole(roles, [ROLE_CODES.CHANNEL_STAFF])
  && !hasAnyRole(roles, [ROLE_CODES.CHANNEL_LEADER])
  && !isAdmin;

export const sampleStatusLabel = (status?: string) =>
  status ? SAMPLE_STATUS_LABELS[status] || status : '-';

export const sampleStatusTagType = (status?: string) => {
  if (status === SAMPLE_STATUS.FINISHED) return 'success';
  if (status === SAMPLE_STATUS.REJECTED) return 'error';
  if (status === SAMPLE_STATUS.CLOSED) return 'default';
  if (status === SAMPLE_STATUS.SHIPPED) return 'info';
  if (status === SAMPLE_STATUS.PENDING_SHIP || status === SAMPLE_STATUS.PENDING_AUDIT) return 'warning';
  return 'primary';
};

export const buildSampleFlowSteps = (status?: string) => {
  const currentIndex = SAMPLE_FLOW_STATUS_ORDER.indexOf(status as SampleStatus);
  const isTerminalHiddenFlow = status === SAMPLE_STATUS.REJECTED || status === SAMPLE_STATUS.CLOSED;
  return SAMPLE_FLOW_STATUS_ORDER.map((key, index) => ({
    key,
    label: sampleStatusLabel(key),
    state: isTerminalHiddenFlow
      ? 'muted'
      : index < currentIndex
        ? 'done'
        : index === currentIndex
          ? 'active'
          : 'pending'
  }));
};

export const channelStaffWaitingHint = (status?: string) => {
  if (status === SAMPLE_STATUS.PENDING_AUDIT) return '等待招商审核';
  if (status === SAMPLE_STATUS.PENDING_SHIP) return '等待运营发货';
  if (status === SAMPLE_STATUS.SHIPPED) return '已发货，等待签收';
  return '';
};

export const channelStaffWaitingTagType = (status?: string) =>
  status === SAMPLE_STATUS.PENDING_SHIP ? 'warning' : 'info';
