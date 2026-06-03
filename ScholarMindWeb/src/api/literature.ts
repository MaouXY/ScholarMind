import type {
  FileAsset,
  Literature,
  LiteratureCategory,
  LiteraturePayload,
  LiteratureQuery,
  PageResult,
} from '@/types/literature'

import request from '@/utils/request'

export function listLiteratures(params: LiteratureQuery) {
  return request.get<never, PageResult<Literature>>('/literatures', { params })
}

export function getLiterature(id: number) {
  return request.get<never, Literature>(`/literatures/${id}`)
}

export function createLiterature(payload: LiteraturePayload) {
  return request.post<never, Literature>('/literatures', payload)
}

export function updateLiterature(id: number, payload: LiteraturePayload) {
  return request.put<never, Literature>(`/literatures/${id}`, payload)
}

export function deleteLiterature(id: number) {
  return request.delete<never, void>(`/literatures/${id}`)
}

export function submitLiterature(id: number) {
  return request.post<never, Literature>(`/literatures/${id}/submit`)
}

export function markLiteratureRead(id: number) {
  return request.post<never, Literature>(`/literatures/${id}/read`)
}

export function listCategories() {
  return request.get<never, LiteratureCategory[]>('/literature-categories')
}

export function listPendingLiteratures(params: { page: number; size: number }) {
  return request.get<never, PageResult<Literature>>('/admin/literatures/pending', {
    params,
  })
}

export function approveLiterature(id: number) {
  return request.post<never, Literature>(`/admin/literatures/${id}/approve`)
}

export function rejectLiterature(id: number, reason: string) {
  return request.post<never, Literature>(`/admin/literatures/${id}/reject`, {
    reason,
  })
}

export function uploadLiteratureFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<never, FileAsset>('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
