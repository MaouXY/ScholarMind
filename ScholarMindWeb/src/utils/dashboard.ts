import type { AdminDashboard } from '@/types/admin'

export function emptyDashboard(): AdminDashboard {
  return {
    overview: {
      userTotal: 0, studentTotal: 0, activeUserTotal: 0,
      literatureTotal: 0, publishedLiteratureTotal: 0, pendingReviewTotal: 0,
      privateLiteratureTotal: 0, fileTotal: 0, fileSizeBytes: 0,
      favoriteTotal: 0, readingHistoryTotal: 0, readingNoteTotal: 0, qaTotal: 0,
      ragDocumentTotal: 0, aiTaskTotal: 0, aiTaskSuccessTotal: 0, aiTaskFailedTotal: 0,
      aiSuccessRate: 0, avgAiDurationMs: 0,
    },
    trends: [], literatureStatusStats: [], literatureCategoryStats: [],
    userRoleStats: [], hotLiteratures: [], activeUsers: [], aiTaskHealth: [],
  }
}
