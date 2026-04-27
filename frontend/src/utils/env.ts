const envLabel = String(import.meta.env.VITE_ENV_LABEL || '').toUpperCase()

export const isTestEnv = import.meta.env.DEV || envLabel.includes('MOCK') || envLabel.includes('TEST')
