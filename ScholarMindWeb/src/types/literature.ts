/** 文献类别：与后端 LiteratureRequest 的 @Pattern 保持一致 */
export type LiteratureType = '学术期刊' | '学位论文（硕士）' | '学位论文（博士）' | '报纸'

export const LITERATURE_TYPE_OPTIONS: LiteratureType[] = [
  '学术期刊',
  '学位论文（硕士）',
  '学位论文（博士）',
  '报纸',
]

/** 检索维度：对应后端 searchField 参数 */
export type LiteratureSearchField =
  | 'theme'
  | 'keywords'
  | 'fulltext'
  | 'author'
  | 'abstract'
  | 'references'
  | 'source'

export const SEARCH_FIELD_OPTIONS: { value: LiteratureSearchField; label: string }[] = [
  { value: 'theme', label: '主题' },
  { value: 'keywords', label: '关键词' },
  { value: 'fulltext', label: '全文' },
  { value: 'author', label: '作者' },
  { value: 'abstract', label: '摘要' },
  { value: 'references', label: '参考文献' },
  { value: 'source', label: '文献来源' },
]

import type { Id } from '@/types/auth'

// 重新导出 Id，便于各视图从 '@/types/literature' 统一引用
export type { Id }

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
  literatureType?: LiteratureType | string
  language?: string
  categoryId?: Id | null
  categoryName?: string | null
  publishDate?: string | null
  onlinePublishDate?: string | null
  uploaderId: Id
  fileId?: Id | null
  fileUrl?: string | null
  fileType?: string | null
  fileSize?: number | null
  abstractText?: string
  aiSummary?: string
  referencesText?: string
  keywords?: string
  readCount?: number
  downloadCount?: number
  citationCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface LiteratureQuery {
  page: number
  size: number
  keyword?: string
  /** 检索维度：topic/keywords/fulltext/author/abstract/references/source */
  searchField?: LiteratureSearchField | string
  categoryId?: Id
  literatureType?: LiteratureType | string
  yearFrom?: number
  yearTo?: number
}

export interface LiteraturePayload {
  title: string
  author?: string
  source?: string
  literatureType?: LiteratureType | string
  language?: string
  categoryId?: Id
  publishDate?: string | null
  onlinePublishDate?: string | null
  fileId?: Id
  abstractText?: string
  keywords?: string
  referencesText?: string
  citationCount?: number
}

export interface LiteratureChunk {
  id: Id
  literatureId: Id
  chunkIndex: number
  pageNo?: number | null
  content: string
  tokenCount?: number | null
  vectorStoreStatus?: string | null
}

export interface LiteratureReferenceChunk {
  id: Id
  literatureId: Id
  chunkIndex?: number
  content: string
  score: number
  /** 该分块在 BM25 关键词召回中的名次（1 起，未命中为 null） */
  sparseRank?: number | null
  /** 该分块在 Milvus 向量召回中的名次（1 起，未命中为 null） */
  denseRank?: number | null
  /** RRF 融合得分（Σ 1/(k+rank)，k=60，越大越靠前） */
  rrfScore?: number | null
  /** 命中来源：HYBRID / SPARSE / DENSE */
  retriever?: string | null
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
  parsedIntent?: string | null
}

/** 检索消融对比中的单条命中（关键词 / 向量 / 融合三列结构一致） */
export interface RetrievalHit {
  chunkId: Id
  literatureId: Id
  literatureTitle: string
  chunkIndex?: number | null
  content: string
  score: number
  /** 该列内部名次（1 起） */
  rank: number
  sparseRank?: number | null
  denseRank?: number | null
  rrfScore?: number | null
}

/** POST /search/retrieval-compare：纯关键词 vs 纯向量 vs RRF 融合 */
export interface RetrievalCompareResult {
  query: string
  /** 向量检索是否可用（Milvus 未就绪或分块未建向量时为 false） */
  denseAvailable: boolean
  limit: number
  sparse: RetrievalHit[]
  dense: RetrievalHit[]
  hybrid: RetrievalHit[]
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
  literatureTitle?: string | null
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
  literatureTitle: string
  author?: string | null
  categoryName?: string | null
  readCount: number
  /** 所属收藏夹；null 表示未分类 */
  folderId?: Id | null
  folderName?: string | null
  createdAt?: string
}

/** 收藏夹（GET /favorites/folders） */
export interface FavoriteFolder {
  id: Id
  name: string
  description?: string | null
  sort?: number
  favoriteCount: number
  createdAt?: string
}

export interface FavoriteFolderPayload {
  name: string
  description?: string | null
  sort?: number
}

/** 我的书房 · 阅读历史列表项（GET /reading-history） */
export interface ReadingHistoryItem {
  id: Id
  literatureId: Id
  literatureTitle: string
  author?: string | null
  categoryName?: string | null
  lastPage: number
  progress: number
  readDuration: number
  updatedAt?: string
}

/** 文献原文下载票据（GET /literatures/{id}/download），url 为 RustFS 预签名地址 */
export interface LiteratureDownloadTicket {
  literatureId: Id
  fileName: string
  contentType: string
  sizeBytes: number
  url: string
  expiresInSeconds: number
  downloadCount: number
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
