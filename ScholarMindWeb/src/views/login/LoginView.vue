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
  username: 'student01',
  password: '123456',
  role: 'STUDENT',
})

function applyRoleDefaults() {
  if (form.role === 'ADMIN') {
    form.username = 'admin'
  } else if (form.role === 'TEACHER') {
    form.username = 'teacher01'
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
      <p class="login-hero__eyebrow">Stage 2 Platform</p>
      <h1>ScholarMind</h1>
      <p class="login-hero__text">
        面向毕业论文与科研学习场景的 AI 文献检索、精读、摘要和知识库管理工作台。
      </p>
      <div class="login-hero__chips" aria-label="平台能力">
        <span>语义检索</span>
        <span>论文精读</span>
        <span>阅读笔记</span>
        <span>知识库治理</span>
      </div>
    </section>

    <section class="login-card" aria-label="登录表单">
      <div class="login-card__header">
        <h2>进入平台</h2>
        <p>使用演示账号或后端真实账号登录。</p>
      </div>

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="角色">
          <el-segmented
            v-model="form.role"
            :options="[
              { label: '学生', value: 'STUDENT' },
              { label: '教师', value: 'TEACHER' },
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
