import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 双栈监听：localhost 在本机同时解析到 ::1 和 127.0.0.1，
    // 只绑 0.0.0.0 时浏览器走 IPv6 会先等 2 秒超时再回退，dev 页面模块多，表现为一直转圈。
    host: '::',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8992',
        changeOrigin: true,
      },
    },
  },
})
