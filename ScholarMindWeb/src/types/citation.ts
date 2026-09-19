import type { Id } from '@/types/auth'

// 重新导出 Id，便于引用相关视图从 '@/types/citation' 统一引用
export type { Id }

export type CitationStyle = 'GB_T_7714' | 'APA' | 'MLA'

/** 后端即时生成的三种引用格式 */
export interface CitationStyleItem {
  style: CitationStyle
  styleLabel: string
  text: string
}

/** 已收藏的引用记录 */
export interface CitationRecord {
  id: Id
  literatureId: Id
  literatureTitle: string
  style: CitationStyle
  styleLabel: string
  citationText: string
  note?: string | null
  createdAt?: string
}

export interface CitationPayload {
  literatureId: Id
  style: CitationStyle
  note?: string
}

export interface CitationQuery {
  page: number
  size: number
  literatureId?: Id
}
