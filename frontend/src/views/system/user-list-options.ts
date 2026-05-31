export const DEPT_TYPE_DEPARTMENT = 'department';

export const GROUP_TYPES = new Set(['recruiter_group', 'channel_group', 'ops_group']);

export interface DeptNode {
  id?: string | number | null;
  deptName?: string | null;
  deptType?: string | null;
  children?: DeptNode[] | null;
}

export interface SelectOption {
  label: string;
  value: string;
}

export function flattenDeptTree(nodes: DeptNode[] = [], bucket: DeptNode[] = []) {
  for (const node of nodes || []) {
    bucket.push(node);
    if (Array.isArray(node.children) && node.children.length) {
      flattenDeptTree(node.children, bucket);
    }
  }
  return bucket;
}

export function isDepartmentNode(node: DeptNode) {
  const type = String(node?.deptType || DEPT_TYPE_DEPARTMENT);
  return type === DEPT_TYPE_DEPARTMENT || type === 'BUSINESS';
}

export function isGroupNode(node: DeptNode) {
  return GROUP_TYPES.has(String(node?.deptType || ''));
}

export function groupTypeLabel(deptType: string) {
  if (deptType === 'recruiter_group') return '招商组';
  if (deptType === 'channel_group') return '渠道组';
  if (deptType === 'ops_group') return '运营组';
  return deptType;
}

export function toTreeSelectOptions(
  nodes: DeptNode[] = [],
  predicate: (node: DeptNode) => boolean
): any[] {
  return (nodes || [])
    .map((node) => {
      const children = toTreeSelectOptions(node.children || [], predicate);
      const allowed = predicate(node);
      if (!allowed && !children.length) {
        return null;
      }
      return {
        key: String(node.id),
        label: node.deptName,
        value: String(node.id),
        disabled: !allowed,
        children: children.length ? children : undefined
      };
    })
    .filter(Boolean);
}

export function toGroupSelectOptions(nodes: DeptNode[] = []): SelectOption[] {
  return flattenDeptTree(nodes)
    .filter(isGroupNode)
    .map((item) => ({
      label: `${item.deptName || item.id}${item.deptType ? ` (${groupTypeLabel(String(item.deptType))})` : ''}`,
      value: String(item.id)
    }));
}

import { ROLE_NAME_MAP } from '../../constants/rbac';

export function sanitizeRoleName(role: { roleCode?: unknown; roleName?: unknown }) {
  const roleCode = String(role?.roleCode || '');
  const rawName = String(role?.roleName || '').trim();
  if (rawName && !/^\?+$/.test(rawName)) {
    return rawName;
  }
  // 自定义角色走中文映射表兜底，永远不展示原始 code
  return ROLE_NAME_MAP[roleCode] ?? '自定义角色';
}
