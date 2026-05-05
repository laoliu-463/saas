<template>
  <n-config-provider :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { dateZhCN, zhCN } from 'naive-ui'

const themeOverrides = {
  common: {
    primaryColor: '#FF4757',
    primaryColorHover: '#FF6B81',
    primaryColorPressed: '#E03140',
    borderRadius: '10px',
    boxShadow2: '0 8px 30px rgba(0, 0, 0, 0.06)',
    boxShadow3: '0 16px 40px rgba(255, 71, 87, 0.1)'
  },
  Button: {
    borderRadiusMedium: '8px',
    heightMedium: '36px',
    fontWeight: '600'
  },
  Card: {
    borderRadius: '16px',
    color: 'rgba(255, 255, 255, 0.85)',
    boxShadow: '0 8px 30px rgba(0, 0, 0, 0.04)',
    borderColor: 'rgba(0, 0, 0, 0.04)'
  },
  Tag: {
    borderRadius: '6px'
  },
  Menu: {
    borderRadius: '8px',
    itemHeight: '44px'
  }
}
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');

body {
  margin: 0;
  font-family: 'Plus Jakarta Sans', var(--font-family);
  background-color: var(--bg-page);
  background-image: 
    radial-gradient(circle at 15% 50%, rgba(255, 71, 87, 0.05), transparent 25%),
    radial-gradient(circle at 85% 30%, rgba(59, 130, 246, 0.04), transparent 25%),
    radial-gradient(circle at 50% 100%, rgba(255, 107, 129, 0.03), transparent 40%);
  background-attachment: fixed;
  color: var(--text-primary);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  min-height: 100vh;
}
</style>
