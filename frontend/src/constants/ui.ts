/** UI layout constants — keep aligned with styles/tokens.css */

export const MODAL_WIDTH = {
  sm: 'var(--modal-width-sm)',
  md: 'var(--modal-width-md)',
  lg: 'var(--modal-width-lg)',
  xl: 'var(--modal-width-xl)',
  xxl: 'var(--modal-width-2xl)'
} as const

export const DRAWER_WIDTH = {
  md: 'var(--drawer-width-md)',
  lg: 'var(--drawer-width-lg)'
} as const

/** Naive drawer `width` prop expects number or px string */
export const DRAWER_WIDTH_PX = {
  md: 640,
  lg: 860
} as const
