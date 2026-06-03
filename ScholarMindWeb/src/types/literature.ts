export type LiteratureStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'

export type LiteratureVisibility = 'PRIVATE' | 'PUBLIC'

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface LiteratureCategory {
  id: number
  name: string
  parentId?: number | null
  sort: number
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface Literature {
  id: number
  title: string
  author?: string
  source?: string
  publishYear?: number
  language?: string
  categoryId?: number | null
  categoryName?: string | null
  uploaderId: number
  visibility: LiteratureVisibility
  status: LiteratureStatus
  fileId?: number | null
  fileUrl?: string | null
  fileType?: string | null
  fileSize?: number | null
  abstractText?: string
  aiSummary?: string
  keywords?: string
  readCount?: number
  downloadCount?: number
  reviewedBy?: number | null
  reviewedAt?: string | null
  rejectReason?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface LiteratureQuery {
  page: number
  size: number
  keyword?: string
  categoryId?: number
  status?: LiteratureStatus
  visibility?: LiteratureVisibility
}

export interface LiteraturePayload {
  title: string
  author?: string
  source?: string
  publishYear?: number
  language?: string
  categoryId?: number
  visibility: LiteratureVisibility
  fileId?: number
  abstractText?: string
  keywords?: string
}

export interface FileAsset {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  publicUrl?: string
  bizType?: string
  bizId?: number
  createdAt?: string
}
