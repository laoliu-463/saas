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
    borderRadius: '10px'
  },
  Button: {
    borderRadiusMedium: '8px'
  },
  Card: {
    borderRadius: '12px'
  },
  Tag: {
    borderRadius: '4px'
  }
}
</script>

<style>
body {
  margin: 0;
  font-family: var(--font-family);
  background-color: var(--bg-page);
  color: var(--text-primary);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  min-height: 100vh;
}
</style>
