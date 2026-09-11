import type { Id } from '@/types/auth'
import type { PageResult } from '@/types/literature'

export type { Id }

export interface KnowledgeDocument {
  id: Id
  title: string
  categoryId?: Id | null
  content: string
  enabled: number
  chunkCount: number
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeDocumentPayload {
  title: string
  categoryId?: Id
  content: string
  enabled: number
}

export type KnowledgePage = PageResult<KnowledgeDocument>
