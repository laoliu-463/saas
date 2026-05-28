<!--
  AppLayout - 应用主布局组件

  用途：定义整个应用的页面骨架，包含顶部导航栏(Header)、左侧菜单栏(Sider)
  和主内容区域的三段式布局结构。

  布局结构：Header 在顶部，下方左侧为 Sider 侧边菜单，右侧为页面内容区。
  内容区顶部始终显示权限提示条(PermissionHintAlert)，下方为路由视图。

  页面切换动画：使用 Vue transition 实现 fade 过渡效果（进入时从下方淡入，离开时向上淡出）。

  使用场景：所有登录后的页面都渲染在此布局内。
-->
<template>
  <!-- 应用根容器：纵向 flex 布局，占满视口高度 -->
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <Header />
    <!-- Naive UI 布局容器，启用侧边栏模式 -->
    <n-layout has-sider class="app-body-layout">
      <!-- 左侧菜单栏 -->
      <Sider />
      <!-- 右侧主内容区 -->
      <n-layout-content class="app-content">
        <div class="app-content-inner">
          <!-- 全局权限不足提示条，始终渲染在内容区顶部 -->
          <PermissionHintAlert />
          <!-- 路由视图：使用动态组件 + transition 实现页面切换动画 -->
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <!-- :key 绑定 fullPath 确保路由变化时触发动画重新播放 -->
              <component :is="Component" v-if="Component" :key="route.fullPath" />
            </transition>
          </router-view>
        </div>
      </n-layout-content>
    </n-layout>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import Header from './Header.vue'
import Sider from './Sider.vue'
import PermissionHintAlert from '../../components/PermissionHintAlert.vue'
import { clearGlobalPermissionHint } from '../../stores/permissionHint'

const route = useRoute()

/**
 * 监听路由变化：每次页面跳转时清除全局权限提示，
 * 避免上一页的权限提示残留在新页面中
 */
watch(() => route.fullPath, () => {
  clearGlobalPermissionHint()
})
</script>

<style scoped>
/* 应用根容器：纵向 flex，100vh 全屏，隐藏溢出 */
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

/* 主体布局区：Header 下方占据剩余空间 */
.app-body-layout {
  flex: 1;
  overflow: hidden;
}

/* 穿透 Naive UI 内部，使滚动容器可纵向滚动 */
.app-body-layout :deep(.n-layout-scroll-container) {
  overflow-y: auto;
}

/* 内容区内边距，使用 CSS 变量统一间距 */
.app-content-inner {
  padding: var(--content-gap, 16px);
}

/* 主内容区：允许纵向滚动，透明背景以配合整体风格 */
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
/* 过渡激活状态：淡入淡出 + 位移，持续 0.2 秒 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

/* 进入起始状态：从下方 6px 处淡入 */
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

/* 离开结束状态：向上 4px 淡出 */
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
