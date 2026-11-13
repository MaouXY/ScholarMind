<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import RequestFailures from '@/components/RequestFailures.vue'
import type { LoginForm } from '@/types/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const submitting = ref(false)

const form = reactive<LoginForm>({
  username: 'student01',
  password: '123456',
  role: 'STUDENT',
})

function applyRoleDefaults() {
  if (form.role === 'ADMIN') {
    form.username = 'admin'
  } else {
    form.username = 'student01'
  }
}

async function handleLogin() {
  submitting.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : authStore.defaultRoute
    await router.push(redirect)
  } catch (error) {
    console.error(
      'login failed, scenario=auth-login, username=%s, role=%s',
      form.username,
      form.role,
      error,
    )
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <h1>ScholarMind</h1>
      <p class="login-hero__text">
        文献检索与阅读助手
      </p>
    </section>

    <section class="login-card" aria-label="登录表单">
      <div class="login-card__header">
        <h2>进入平台</h2>
        <p>使用你的平台账号登录</p>
      </div>

      <RequestFailures />
      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="角色">
          <el-segmented
            v-model="form.role"
            :options="[
              { label: '用户', value: 'STUDENT' },
              { label: '管理员', value: 'ADMIN' },
            ]"
            block
            @change="applyRoleDefaults"
          />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button
          class="login-card__submit"
          type="primary"
          :loading="submitting"
          @click="handleLogin"
        >
          进入工作台
        </el-button>
      </el-form>
    </section>
  </div>
</template>
