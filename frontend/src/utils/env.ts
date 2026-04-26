const envLabel = String(import.meta.env.VITE_ENV_LABEL || '').toUpperCase()

export const isMockEnv = import.meta.env.DEV || envLabel.includes('MOCK')
