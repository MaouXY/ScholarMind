import axios from 'axios'
import { ElMessage } from 'element-plus'

import router from '@/router'
import { clearToken, getToken } from '@/utils/storage'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** AI 类请求（摘要、问答、向量索引、运营报告等）通常需要 20~90 秒 */
export const AI_REQUEST_TIMEOUT = 120000

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>
    if (
      typeof payload?.code === 'number' &&
      payload.code !== 0 &&
      payload.code !== 200
    ) {
      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(payload)
    }
    return payload?.data ?? response.data
  },
  (error) => {
    const status = error?.response?.status
    const isTimeout =
      error?.code === 'ECONNABORTED' ||
      error?.code === 'ETIMEDOUT' ||
      /timeout/i.test(String(error?.message ?? ''))
    if (status === 401) {
      if (import.meta.env.VITE_USE_MOCK_AUTH === 'true') {
        console.error(
          'request unauthorized in mock mode, scenario=http-unauthorized-mock, url=%s',
          error?.config?.url,
          error,
        )
      } else {
        clearToken()
        ElMessage.error('登录已失效，请重新登录')
        router.push('/login')
      }
    } else if (isTimeout) {
      ElMessage.error('AI 处理较慢，已超过等待时间，请稍后重试')
    } else {
      ElMessage.error(
        error?.response?.data?.message || error.message || '网络异常',
      )
    }
    return Promise.reject(error)
  },
)

export default request
