<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { LoginForm } from '@/types/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const submitting = ref(false)

const form = reactive<LoginForm>({
  username: 'demo',
  password: '123456',
  role: 'operator',
})

async function handleLogin() {
  submitting.value = true
  try {
    await authStore.login(form)
    ElMessage.success('Login successful')
    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : authStore.defaultRoute
    await router.push(redirect)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <p class="login-hero__eyebrow">Stage 0</p>
      <h1>Reusable frontend workspace</h1>
      <p class="login-hero__text">
        Vue 3, Element Plus, Pinia, Router, Axios, and ECharts are wired in and
        ready to support a different business theme without rebuilding the
        shell.
      </p>
      <div class="login-hero__chips">
        <span>Workspace A</span>
        <span>Workspace B</span>
        <span>Admin Center</span>
        <span>Analytics</span>
      </div>
    </section>

    <section class="login-card">
      <div class="login-card__header">
        <h2>Account sign in</h2>
        <p>
          Stage 0 uses local mock auth so the shell can be reused before real
          API integration.
        </p>
      </div>

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="Role">
          <el-segmented
            v-model="form.role"
            :options="[
              { label: 'Operator', value: 'operator' },
              { label: 'Reviewer', value: 'reviewer' },
              { label: 'Admin', value: 'admin' },
            ]"
            block
          />
        </el-form-item>
        <el-form-item label="Username">
          <el-input v-model="form.username" placeholder="Enter username" />
        </el-form-item>
        <el-form-item label="Password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="Enter password"
          />
        </el-form-item>
        <el-button
          class="login-card__submit"
          type="primary"
          :loading="submitting"
          @click="handleLogin"
        >
          Enter workspace
        </el-button>
      </el-form>
    </section>
  </div>
</template>
