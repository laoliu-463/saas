/**
 * 分页工具函数单元测试
 *
 * 测试分页常量、参数标准化逻辑：
 * 1. 常量值和 createPaginationState 默认状态
 * 2. normalizePage / normalizePageSize 对非法输入的容错和边界截断
 */

import { describe, expect, it } from 'vitest'
import {
  DEFAULT_PAGE_SIZE,
  MAX_PAGE_SIZE,
  PAGE_SIZE_OPTIONS,
  createPaginationState,
  normalizePage,
  normalizePageSize
} from './pagination'

describe('pagination helpers', () => {
  // 验证默认常量值和初始分页状态的正确性
  it('uses the project default page size and exposes the maximum selectable size', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(20)      // 默认每页 20 条
    expect(MAX_PAGE_SIZE).toBe(100)         // 最大每页 100 条
    expect(PAGE_SIZE_OPTIONS).toEqual([20, 50, 100]) // 可选每页条数列表
    expect(createPaginationState()).toMatchObject({
      page: 1,              // 默认第 1 页
      pageSize: 20,         // 默认每页 20 条
      itemCount: 0,         // 初始总条数为 0
      showSizePicker: true, // 显示每页条数选择器
      pageSizes: [20, 50, 100]
    })
  })

  // 验证非法输入的标准化行为：非数字回退默认值，超出范围的截断
  it('normalizes invalid page numbers and caps page size at 100', () => {
    expect(normalizePage(0)).toBe(1)         // 0 非法，回退默认 1
    expect(normalizePage('abc')).toBe(1)     // 非数字字符串，回退默认 1
    expect(normalizePage(3.8)).toBe(3)       // 浮点数向下取整
    expect(normalizePageSize(0)).toBe(20)    // 0 非法，回退默认 20
    expect(normalizePageSize('abc')).toBe(20)// 非数字字符串，回退默认 20
    expect(normalizePageSize(500)).toBe(100) // 超出最大值，截断到 100
  })
})
