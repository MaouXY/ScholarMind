import axios from 'axios'
import { ElMessage } from 'element-plus'

import router from '@/router'
import { clearToken, getToken } from '@/utils/storage'
import { normalizeMetrics, requestFailures, requestFailureLabel } from '@/utils/requestState'

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
      requestFailures.set(response.config.url ?? '', payload.message || '请求失败')
      console.error('business request failed, scenario=http-response, url=%s, code=%s', response.config.url, payload.code, payload)
      return Promise.reject(payload)
    }
    requestFailures.delete(response.config.url ?? '')
    return normalizeMetrics(payload?.data ?? response.data) as never
  },
  (error) => {
    const status = error?.response?.status
    const url = String(error?.config?.url ?? '')
    console.error('request failed, scenario=http-request, method=%s, url=%s, status=%s', error?.config?.method, url, status, error)
    if (status !== 401) requestFailures.set(url, requestFailureLabel(url))
    const isTimeout =
      error?.code === 'ECONNABORTED' ||
      error?.code === 'ETIMEDOUT' ||
      /timeout/i.test(String(error?.message ?? ''))
    if (status === 401) {
      clearToken()
      ElMessage.error('登录已失效，请重新登录')
      router.push('/login')
    } else if (isTimeout) {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error(
        error?.response?.data?.message || '服务暂时不可用，请稍后重试',
      )
    }
    return Promise.reject(error)
  },
)

export default request
