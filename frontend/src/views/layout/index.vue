<template>
  <n-layout has-sider style="height: 100vh;">
    <Sider />
    <n-layout>
      <Header />
      <n-layout-content content-style="padding: 24px; background: var(--bg-color);">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup lang="ts">
import Header from './Header.vue';
import Sider from './Sider.vue';
</script>
