<template>
  <div class="app-layout">
    <Header />
    <n-layout has-sider class="app-body-layout">
      <Sider />
      <n-layout-content class="app-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" v-if="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </n-layout-content>
    </n-layout>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import Header from './Header.vue'
import Sider from './Sider.vue'

const route = useRoute()
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.app-body-layout {
  flex: 1;
  overflow: hidden;
}

.app-body-layout :deep(.n-layout-scroll-container) {
  overflow-y: auto;
}

.app-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
  background: transparent;
}

.app-body-layout {
  background: transparent !important;
}

/* ---- 页面过渡动画 ---- */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
