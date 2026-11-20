<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { RegisterForm } from '@/types/auth'

const authStore = useAuthStore()
const router = useRouter()
const submitting = ref(false)

const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
})

function validate() {
  const username = form.username.trim()
  if (!username) {
    ElMessage.warning('请输入账号')
    return false
  }
  if (username.length < 3 || username.length > 32) {
    ElMessage.warning('账号长度需为 3-32 个字符')
    return false
  }
  if (!form.password || form.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return false
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  return true
}

async function handleRegister() {
  if (!validate()) {
    return
  }
  submitting.value = true
  try {
    await authStore.register({
      username: form.username.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
    })
    ElMessage.success('注册成功，已自动登录')
    await router.push(authStore.defaultRoute)
  } catch (error) {
    console.error(
      'register failed, scenario=auth-register, username=%s',
      form.username,
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
      <p class="login-hero__eyebrow">学术文献智能服务平台</p>
      <h1>ScholarMind</h1>
      <p class="login-hero__text">
        注册一个账号，开始管理你的文献、笔记与阅读进度。
      </p>
      <div class="login-hero__chips" aria-label="平台能力">
        <span>语义检索</span>
        <span>论文精读</span>
        <span>阅读笔记</span>
        <span>知识库治理</span>
      </div>
    </section>

    <section class="login-card" aria-label="注册表单">
      <div class="login-card__header">
        <h2>创建账号</h2>
        <p>只需填写账号并两次输入密码即可完成注册。</p>
      </div>

      <el-form label-position="top" @submit.prevent="handleRegister">
        <el-form-item label="账号">
          <el-input
            v-model="form.username"
            placeholder="3-32 个字符"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="至少 6 位"
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
            autocomplete="new-password"
            @keyup.enter="handleRegister"
          />
        </el-form-item>
        <el-button
          class="login-card__submit"
          type="primary"
          :loading="submitting"
          @click="handleRegister"
        >
          注册并登录
        </el-button>
        <el-button class="login-card__submit" text @click="router.push('/login')">
          已有账号？返回登录
        </el-button>
      </el-form>
    </section>
  </div>
</template>
