import type { PageResult } from '@/types/literature'

export interface KnowledgeDocument {
  id: number
  title: string
  categoryId?: number | null
  content: string
  enabled: number
  chunkCount: number
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeDocumentPayload {
  title: string
  categoryId?: number
  content: string
  enabled: number
}

export type KnowledgePage = PageResult<KnowledgeDocument>
