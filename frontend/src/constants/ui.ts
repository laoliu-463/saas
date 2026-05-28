/**
 * UI 布局常量模块
 *
 * 职责：
 * - 定义弹窗（Modal）宽度的 CSS 变量映射
 * - 定义抽屉（Drawer）宽度的 CSS 变量映射和像素值
 * - 保持与 styles/tokens.css 中的 CSS 变量同步
 *
 * 使用说明：
 * - MODAL_WIDTH / DRAWER_WIDTH 使用 CSS 变量，支持主题动态切换
 * - DRAWER_WIDTH_PX 使用固定像素值，用于 Naive UI 的 width 属性（需要 number 或 px 字符串）
 */

/** UI layout constants — keep aligned with styles/tokens.css */

/**
 * 弹窗宽度 CSS 变量映射
 * 从小到 2xl 五个尺寸档位
 */
export const MODAL_WIDTH = {
  sm: 'var(--modal-width-sm)',   // 小型弹窗
  md: 'var(--modal-width-md)',   // 中型弹窗
  lg: 'var(--modal-width-lg)',   // 大型弹窗
  xl: 'var(--modal-width-xl)',   // 超大弹窗
  xxl: 'var(--modal-width-2xl)'  // 巨型弹窗
} as const

/**
 * 抽屉宽度 CSS 变量映射
 * 两个尺寸档位
 */
export const DRAWER_WIDTH = {
  md: 'var(--drawer-width-md)',  // 中型抽屉
  lg: 'var(--drawer-width-lg)'   // 大型抽屉
} as const

/**
 * 抽屉宽度像素值
 * Naive UI drawer 的 width 属性接受 number 或 px 字符串
 * 这里提供预定义的像素值，便于直接传入组件
 */
export const DRAWER_WIDTH_PX = {
  md: 640,  // 中型抽屉：640px
  lg: 860   // 大型抽屉：860px
} as const
