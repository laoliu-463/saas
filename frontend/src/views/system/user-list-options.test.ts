import { describe, expect, it } from 'vitest';
import {
  isDepartmentNode,
  sanitizeRoleName,
  toGroupSelectOptions,
  toTreeSelectOptions
} from './user-list-options';

describe('user list organization option helpers', () => {
  const tree = [
    {
      id: 'biz',
      deptName: '招商组',
      deptType: 'recruiter_group',
      children: []
    },
    {
      id: 'channel',
      deptName: '渠道组',
      deptType: 'channel_group',
      children: []
    }
  ];

  it('exposes top-level business groups as group options', () => {
    expect(toGroupSelectOptions(tree)).toEqual([
      { label: '招商组 (招商组)', value: 'biz' },
      { label: '渠道组 (渠道组)', value: 'channel' }
    ]);
  });

  it('keeps department options empty when the tree only contains groups', () => {
    expect(toTreeSelectOptions(tree, isDepartmentNode)).toEqual([]);
  });

  it('falls back from corrupted role names to readable labels', () => {
    expect(sanitizeRoleName({ roleCode: 'colonel_leader', roleName: '????' })).toBe('招商组长（兼容）');
    expect(sanitizeRoleName({ roleCode: 'channel_staff', roleName: '渠道专员' })).toBe('渠道专员');
  });
});
