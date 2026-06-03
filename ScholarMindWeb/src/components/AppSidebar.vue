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
    STUDENT: '学生阅读空间',
    TEACHER: '教师指导空间',
    ADMIN: '平台治理空间',
  }
  return labels[props.role] ?? '阅读空间'
})
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
    <span class="sidebar__halo" aria-hidden="true" />

    <div class="sidebar__brand">
      <span class="sidebar__mark">
        <span>SM</span>
      </span>
      <div v-if="!collapsed">
        <strong>ScholarMind</strong>
        <p>Spatial Literature OS</p>
      </div>
    </div>

    <div v-if="!collapsed" class="sidebar__context">
      <span>当前空间</span>
      <strong>{{ roleLabel }}</strong>
      <small>AI retrieval · RAG ready</small>
    </div>

    <nav class="sidebar__nav" aria-label="主导航">
      <section v-for="group in visibleMenus" :key="group.title" class="sidebar__group">
      <p v-if="!collapsed" class="sidebar__title">{{ group.title }}</p>
      <router-link
        v-for="item in group.items"
        :key="item.path"
        :to="item.path"
        class="sidebar__link"
        :class="{ 'is-active': currentPath === item.path }"
        :aria-label="item.label"
      >
        <span class="sidebar__icon"><el-icon><component :is="item.icon" /></el-icon></span>
        <span v-if="!collapsed">{{ item.label }}</span>
        <i v-if="!collapsed" aria-hidden="true" />
      </router-link>
      </section>
    </nav>

    <div v-if="!collapsed" class="sidebar__footer">
      <span>Reading Focus</span>
      <strong>沉浸式论文精读</strong>
      <small>检索、摘要、批注和知识库协同工作</small>
    </div>
  </aside>
</template>
