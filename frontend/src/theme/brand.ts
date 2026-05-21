/**
 * Brand palette — single source for JS theme (Naive overrides).
 * CSS variables in styles/tokens.css must stay in sync with these values.
 */
export const brand = {
  primary: '#e84555',
  primaryHover: '#f25f6d',
  primaryPressed: '#cf3344',
  primaryLight: 'rgba(232, 69, 85, 0.1)',

  success: '#0d9f6e',
  warning: '#e58a00',
  danger: '#e03e3e',
  info: '#2563eb',

  textPrimary: '#0f172a',
  textSecondary: '#475569',
  textTertiary: '#94a3b8',

  body: '#f3f5f9',
  card: '#ffffff',
  cardMuted: '#f8fafc',
  border: '#e2e8f0',

  shadow1: '0 1px 2px rgba(15, 23, 42, 0.04)',
  shadow2: '0 4px 16px rgba(15, 23, 42, 0.06)',
  shadow3: '0 12px 32px rgba(15, 23, 42, 0.1)',
  shadowModal: '0 20px 48px rgba(15, 23, 42, 0.14)'
} as const
