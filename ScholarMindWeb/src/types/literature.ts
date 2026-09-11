export type LiteratureStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'

export type LiteratureVisibility = 'PRIVATE' | 'PUBLIC'

import type { Id } from '@/types/auth'

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface LiteratureCategory {
  id: Id
  name: string
  parentId?: Id | null
  sort: number
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface Literature {
  id: Id
  title: string
  author?: string
  source?: string
  publishYear?: number
  language?: string
  categoryId?: Id | null
  categoryName?: string | null
  uploaderId: Id
  visibility: LiteratureVisibility
  status: LiteratureStatus
  fileId?: Id | null
  fileUrl?: string | null
  fileType?: string | null
  fileSize?: number | null
  abstractText?: string
  aiSummary?: string
  keywords?: string
  readCount?: number
  downloadCount?: number
  reviewedBy?: Id | null
  reviewedAt?: string | null
  rejectReason?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface LiteratureQuery {
  page: number
  size: number
  keyword?: string
  categoryId?: Id
  status?: LiteratureStatus
  visibility?: LiteratureVisibility
}

export interface LiteraturePayload {
  title: string
  author?: string
  source?: string
  publishYear?: number
  language?: string
  categoryId?: Id
  visibility: LiteratureVisibility
  fileId?: Id
  abstractText?: string
  keywords?: string
}

export interface LiteratureReferenceChunk {
  id: Id
  literatureId: Id
  chunkIndex?: number
  content: string
  score: number
}

export interface LiteratureSearchPayload {
  query: string
  categoryId?: Id
  limit?: number
}

export interface LiteratureSearchResult {
  query: string
  literatures: Literature[]
  references: LiteratureReferenceChunk[]
  parsedIntent?: string
}

export interface LiteratureSummaryResult {
  literatureId: Id
  summary: string
  status: string
  summaryUpdatedAt?: string
}

export interface LiteratureIndexResult {
  literatureId: Id
  chunkCount: number
  parseStatus: string
}

export interface LiteratureQuestionPayload {
  question: string
  limit?: number
}

export interface LiteratureQuestionResult {
  recordId?: Id
  literatureId: Id
  question: string
  answer: string
  references: LiteratureReferenceChunk[]
  createdAt?: string
}

export interface LiteratureComparePayload {
  literatureIds: Id[]
}

export interface LiteratureCompareResult {
  literatureIds: Id[]
  analysis: string
}

export interface ReadingHistory {
  id?: Id
  literatureId: Id
  lastPage?: number
  progress?: number
  readDuration?: number
  createdAt?: string
  updatedAt?: string
}

export interface ReadingHistoryPayload {
  lastPage?: number
  progress?: number
  readDuration?: number
}

export interface ReadingNote {
  id: Id
  literatureId: Id
  title: string
  content: string
  aiGenerated?: number
  createdAt?: string
  updatedAt?: string
}

export interface ReadingNotePayload {
  literatureId: Id
  title: string
  content: string
  aiGenerated?: number
}

export interface Favorite {
  id: Id
  literatureId: Id
  createdAt?: string
}

export interface LiteratureDetail {
  literature: Literature
  favorited: boolean
  readingHistory?: ReadingHistory | null
  recentNotes: ReadingNote[]
}

export interface FileAsset {
  id: Id
  originalName: string
  contentType: string
  sizeBytes: number
  publicUrl?: string
  bizType?: string
  bizId?: Id
  createdAt?: string
}
