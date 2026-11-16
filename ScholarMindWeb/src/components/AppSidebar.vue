<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'

const props = defineProps<{
  collapsed: boolean
  currentPath: string
  role: string
  menus: Array<{
    title: string
    items: Array<{
      label: string
      path: string
      icon: Component
      role: string
    }>
  }>
}>()

const visibleMenus = computed(() =>
  props.menus
    .map((group) => ({
      ...group,
      items: group.items.filter((entry) => entry.role === props.role),
    }))
    .filter((group) => group.items.length > 0),
)

const roleLabel = computed(() => {
  const labels: Record<string, string> = {
    STUDENT: '用户阅读空间',
    ADMIN: '平台治理空间',
  }
  return labels[props.role] ?? '阅读空间'
})
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">

    <div class="sidebar__brand">
      <span class="sidebar__mark">
        <span>SM</span>
      </span>
      <div v-if="!collapsed">
        <strong>ScholarMind</strong>
        <p>文献检索与阅读助手</p>
      </div>
    </div>

    <p v-if="!collapsed" class="sidebar__role">{{ roleLabel }}</p>

    <nav class="sidebar__nav" aria-label="主导航">
      <section v-for="group in visibleMenus" :key="group.title" class="sidebar__group">
      <p v-if="!collapsed" class="sidebar__title">{{ group.title }}</p>
      <router-link
        v-for="item in group.items"
        :key="item.path"
        :to="item.path"
        class="sidebar__link"
        :class="{ 'is-active': currentPath === item.path || (item.path === '/student/search' && currentPath.startsWith('/student/literature/')) }"
        :aria-label="item.label"
      >
        <span class="sidebar__icon"><el-icon><component :is="item.icon" /></el-icon></span>
        <span v-if="!collapsed">{{ item.label }}</span>
        <i v-if="!collapsed" aria-hidden="true" />
      </router-link>
      </section>
    </nav>

  </aside>
</template>
