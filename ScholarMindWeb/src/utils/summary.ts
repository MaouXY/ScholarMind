/**
 * AI 摘要解析：后端 `literature.ai_summary` 存的是结构化 JSON 字符串
 * （StructuredLiteratureSummary：background / problem / method / conclusion /
 * innovation / keywords / suitableReaders），历史数据或降级结果可能是纯文本，
 * 这里统一解析成「分节 + 关键词」两段结构，供详情页与阅读页共用。
 */

export interface SummarySection {
  key: string
  label: string
  content: string
}

export interface ParsedSummary {
  /** 是否为结构化摘要；false 时 sections 为空、fallbackText 有值 */
  structured: boolean
  sections: SummarySection[]
  keywords: string[]
  fallbackText: string
}

/** 分节顺序与中文标题：与后端 summarySchemaPrompt 的字段一一对应 */
const SECTION_LABELS: { key: string; label: string }[] = [
  { key: 'background', label: '研究背景' },
  { key: 'problem', label: '研究问题' },
  { key: 'method', label: '研究方法' },
  { key: 'conclusion', label: '主要结论' },
  { key: 'innovation', label: '创新点' },
  { key: 'suitableReaders', label: '适合读者' },
]

export function parseAiSummary(raw?: string | null): ParsedSummary {
  const text = (raw ?? '').trim()
  if (!text) {
    return { structured: false, sections: [], keywords: [], fallbackText: '' }
  }

  // 只对「看起来是 JSON 对象」的内容做解析，避免把普通文本当 JSON 抛错
  if (!text.startsWith('{')) {
    return { structured: false, sections: [], keywords: [], fallbackText: text }
  }

  let payload: Record<string, unknown>
  try {
    payload = JSON.parse(text) as Record<string, unknown>
  } catch (error) {
    console.warn(
      'ai summary json parse failed, scenario=literature-summary-parse, fallback=plain-text',
      error,
    )
    return { structured: false, sections: [], keywords: [], fallbackText: text }
  }

  const sections = SECTION_LABELS.map(({ key, label }) => ({
    key,
    label,
    content: stringifyValue(payload[key]),
  })).filter((item) => item.content)

  const keywords = toStringList(payload.keywords)

  // 一个可用分节都没有时，按纯文本兜底，避免页面出现空白摘要
  if (!sections.length && !keywords.length) {
    return { structured: false, sections: [], keywords: [], fallbackText: text }
  }

  return { structured: true, sections, keywords, fallbackText: '' }
}

/** 摘要是否值得渲染（详情页 / 阅读页据此决定是否显示 AI 摘要区块） */
export function hasAiSummary(raw?: string | null): boolean {
  const parsed = parseAiSummary(raw)
  return parsed.structured || Boolean(parsed.fallbackText)
}

function stringifyValue(value: unknown): string {
  if (typeof value === 'string') return value.trim()
  if (Array.isArray(value)) return value.map((item) => String(item)).join('、')
  if (value === null || value === undefined) return ''
  return String(value)
}

function toStringList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => String(item).trim())
      .filter(Boolean)
  }
  if (typeof value === 'string' && value.trim()) {
    return value
      .split(/[,，;；、]/)
      .map((item) => item.trim())
      .filter(Boolean)
  }
  return []
}
