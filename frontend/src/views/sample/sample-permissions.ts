/**
 * 寄样模块权限控制工具
 *
 * 提供基于角色的寄样数据导出权限判断，以及运营角色的 Tab 可见性过滤。
 * 配合 ROLE_CODES 常量，控制不同角色在寄样列表中可看到的状态 Tab 以及是否允许导出。
 */
import { ROLE_CODES } from '../../constants/rbac';

const SAMPLE_APPLY_ROLES = new Set<string>([
  ROLE_CODES.ADMIN,
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.CHANNEL_LEADER,
  ROLE_CODES.CHANNEL_STAFF
]);

const SAMPLE_REVIEW_ROLES = new Set<string>([
  ROLE_CODES.ADMIN,
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.CHANNEL_LEADER,
  ROLE_CODES.CHANNEL_STAFF,
  ROLE_CODES.OPS_STAFF
]);

/** 所有内部角色都可导出自己可见的寄样数据。 */
const SAMPLE_EXPORT_ROLES = new Set<string>([
  ROLE_CODES.ADMIN,
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.OPS_STAFF,
  ROLE_CODES.CHANNEL_LEADER,
  ROLE_CODES.CHANNEL_STAFF
]);

/** 寄样 Tab 选项的基础类型（label 显示文本 + value 状态标识） */
export type SampleTabOption = { label: string; value: string };

/** 运营侧发货台仅处理审核通过后的物流履约，不含审核阶段状态。 */
export const OPS_HIDDEN_SAMPLE_STATUSES = new Set(['PENDING_AUDIT', 'REJECTED']);

/** 运营角色发货台可用的寄样状态 Tab 列表 */
export const OPS_SHIPPING_TABS: SampleTabOption[] = [
  { label: '待发货', value: 'PENDING_SHIP' },
  { label: '快递中', value: 'SHIPPED' },
  { label: '待交作业', value: 'PENDING_TASK' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已关闭', value: 'CLOSED' }
];

export const canApplySamplesByRole = (roles: string[] = []) =>
  roles.some((role) => SAMPLE_APPLY_ROLES.has(role));

export const canReviewSamplesByRole = (roles: string[] = []) =>
  roles.some((role) => SAMPLE_REVIEW_ROLES.has(role));

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
 * 所有内部角色均可导出其数据范围内可见的寄样数据。
 *
 * @param roles - 当前用户的角色编码数组
 * @returns 若拥有导出权限返回 true，否则返回 false
 */
export const canExportSamplesByRole = (roles: string[] = []) =>
  roles.some((role) => SAMPLE_EXPORT_ROLES.has(role));
