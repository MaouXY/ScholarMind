import { reactive } from 'vue'

export const requestFailures = reactive(new Map<string, string>())

export function requestFailureLabel(url: string) {
  if (url.includes('/auth/')) return '登录失败'
  if (url.includes('/search/')) return '检索失败'
  if (url.includes('/statistics/')) return '统计数据加载失败'
  if (url.includes('/question')) return '回答生成失败'
  if (url.includes('/ai-tasks')) return 'AI 调用日志加载失败'
  if (url.includes('/notes')) return '阅读笔记加载失败'
  if (url.includes('/reading-history')) return '阅读记录加载失败'
  if (url.includes('/favorites')) return '收藏数据加载失败'
  return '请求失败'
}

// 只转换计量字段，避免大整数主键被 Number 截断。
export function normalizeMetrics(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeMetrics)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    const metric = /^(total|page|size|pages|count|sort|progress|durationMs|avgDurationMs|avgAiDurationMs|successRate|aiSuccessRate|success|failed|timeout|running|newUsers|newLiteratures|readingEvents|qaEvents|aiTasks|readDuration|activityScore|requestChars|responseChars)$/.test(key)
      || /(Count|Total|Bytes)$/.test(key)
    if (metric && typeof item === 'string' && /^-?\d+(\.\d+)?$/.test(item)) {
      const number = Number(item)
      if (Number.isFinite(number) && Math.abs(number) <= Number.MAX_SAFE_INTEGER) return [key, number]
    }
    return [key, normalizeMetrics(item)]
  }))
}
