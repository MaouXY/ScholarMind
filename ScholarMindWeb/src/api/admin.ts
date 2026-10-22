import type { Id, UserProfile } from '@/types/auth'
import type {
  AdminAiTask,
  AdminAiTaskQuery,
  AdminAiTaskSummary,
  AdminCategoryPayload,
  AdminDashboard,
  AdminKeywordStat,
  AdminOperationReport,
  AdminUserQuery,
} from '@/types/admin'
import type { LiteratureCategory, PageResult } from '@/types/literature'

import request, { AI_REQUEST_TIMEOUT } from '@/utils/request'

export function getAdminDashboard(params: { days?: number; limit?: number }) {
  return request.get<never, AdminDashboard>('/admin/statistics/dashboard', {
    params,
  })
}

export function listAdminUsers(params: AdminUserQuery) {
  return request.get<never, PageResult<UserProfile>>('/admin/users', { params })
}

export function updateAdminUserStatus(id: Id, status: number) {
  return request.put<never, UserProfile>(`/admin/users/${id}/status`, { status })
}

export function listAdminCategories(params: { onlyEnabled?: boolean }) {
  return request.get<never, LiteratureCategory[]>('/literature-categories', {
    params,
  })
}

export function createAdminCategory(payload: AdminCategoryPayload) {
  return request.post<never, LiteratureCategory>(
    '/literature-categories',
    payload,
  )
}

export function updateAdminCategory(id: Id, payload: AdminCategoryPayload) {
  return request.put<never, LiteratureCategory>(
    `/literature-categories/${id}`,
    payload,
  )
}

export function deleteAdminCategory(id: Id) {
  return request.delete<never, void>(`/literature-categories/${id}`)
}

export function listAdminAiTasks(params: AdminAiTaskQuery) {
  return request.get<never, PageResult<AdminAiTask>>('/admin/ai-tasks', {
    params,
  })
}

export function getAdminAiTaskSummary() {
  return request.get<never, AdminAiTaskSummary>('/admin/ai-tasks/summary')
}

export function listAdminKeywords(params: { limit?: number; days?: number }) {
  return request.get<never, AdminKeywordStat[]>('/admin/statistics/keywords', {
    params,
  })
}

export function generateAdminOperationReport(payload: { days?: number }) {
  return request.post<never, AdminOperationReport>(
    '/admin/statistics/report',
    payload,
    { timeout: AI_REQUEST_TIMEOUT },
  )
}
