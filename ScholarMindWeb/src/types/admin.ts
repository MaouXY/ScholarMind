import type { Id, UserRole } from '@/types/auth'

export interface AdminOverview {
  userTotal: number
  studentTotal: number
  teacherTotal: number
  activeUserTotal: number
  literatureTotal: number
  publishedLiteratureTotal: number
  pendingReviewTotal: number
  privateLiteratureTotal: number
  fileTotal: number
  fileSizeBytes: number
  favoriteTotal: number
  readingHistoryTotal: number
  readingNoteTotal: number
  qaTotal: number
  ragDocumentTotal: number
  aiTaskTotal: number
  aiTaskSuccessTotal: number
  aiTaskFailedTotal: number
  aiSuccessRate: number
  avgAiDurationMs: number
}

export interface AdminTrendPoint {
  date: string
  newUsers: number
  newLiteratures: number
  readingEvents: number
  qaEvents: number
  aiTasks: number
}

export interface AdminDimensionStat {
  name: string
  count: number
}

export interface AdminHotLiterature {
  literatureId: Id
  title: string
  author?: string
  categoryName?: string
  status: string
  visibility: string
  readCount: number
  downloadCount: number
  favoriteCount: number
  noteCount: number
}

export interface AdminActiveUser {
  userId: Id
  username: string
  nickname?: string
  role: string
  uploadedLiteratureCount: number
  readingHistoryCount: number
  favoriteCount: number
  noteCount: number
  qaCount: number
  activityScore: number
}

export interface AdminAiTaskHealth {
  taskType: string
  total: number
  success: number
  failed: number
  timeout: number
  successRate: number
  avgDurationMs: number
}

export interface AdminDashboard {
  overview: AdminOverview
  trends: AdminTrendPoint[]
  literatureStatusStats: AdminDimensionStat[]
  literatureCategoryStats: AdminDimensionStat[]
  userRoleStats: AdminDimensionStat[]
  hotLiteratures: AdminHotLiterature[]
  activeUsers: AdminActiveUser[]
  aiTaskHealth: AdminAiTaskHealth[]
}

export interface AdminUserQuery {
  page: number
  size: number
  keyword?: string
  role?: UserRole
  status?: number
}

export interface AdminCategoryPayload {
  name: string
  parentId?: Id | null
  sort: number
  status: number
}

export interface AdminAiTask {
  id: Id
  taskType: string
  bizType?: string
  bizId?: Id
  status: string
  durationMs?: number
  requestChars: number
  responseChars: number
  requestPreview?: string
  responsePreview?: string
  errorMessage?: string
  modelName?: string
  createdAt?: string
  updatedAt?: string
}

export interface AdminAiTaskSummary {
  total: number
  success: number
  failed: number
  timeout: number
  running: number
  successRate: number
  avgDurationMs: number
  items: AdminAiTaskHealth[]
}

export interface AdminAiTaskQuery {
  page: number
  size: number
  taskType?: string
  status?: string
  keyword?: string
}

export interface AdminKeywordStat {
  keyword: string
  literatureCount: number
  searchCount: number
  qaCount: number
  total: number
}

export interface AdminOperationReportSection {
  title: string
  content: string
}

export interface AdminOperationReport {
  generatedAt: string
  days: number
  fallbackUsed: boolean
  summary: string
  highlights: string[]
  risks: string[]
  suggestions: string[]
  sections: AdminOperationReportSection[]
}
