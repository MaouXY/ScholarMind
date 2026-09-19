import type {
  FileAsset,
  Favorite,
  Id,
  Literature,
  LiteratureCategory,
  LiteratureChunk,
  LiteratureComparePayload,
  LiteratureCompareResult,
  LiteratureDetail,
  LiteratureDownloadTicket,
  LiteratureIndexResult,
  LiteraturePayload,
  LiteratureQuestionPayload,
  LiteratureQuestionResult,
  LiteratureQuery,
  LiteratureSearchPayload,
  LiteratureSearchResult,
  LiteratureSummaryResult,
  PageResult,
  ReadingHistory,
  ReadingHistoryItem,
  ReadingHistoryPayload,
  ReadingNote,
  ReadingNotePayload,
} from '@/types/literature'

import request from '@/utils/request'

export function listLiteratures(params: LiteratureQuery) {
  return request.get<never, PageResult<Literature>>('/literatures', { params })
}

export function getLiterature(id: Id) {
  return request.get<never, Literature>(`/literatures/${id}`)
}

export function getLiteratureView(id: Id) {
  return request.get<never, LiteratureDetail>(`/literatures/${id}/view`)
}

export function listLiteratureChunks(id: Id) {
  return request.get<never, LiteratureChunk[]>(`/literatures/${id}/chunks`)
}

export function createLiterature(payload: LiteraturePayload) {
  return request.post<never, Literature>('/literatures', payload)
}

export function updateLiterature(id: Id, payload: LiteraturePayload) {
  return request.put<never, Literature>(`/literatures/${id}`, payload)
}

export function deleteLiterature(id: Id) {
  return request.delete<never, void>(`/literatures/${id}`)
}

export function submitLiterature(id: Id) {
  return request.post<never, Literature>(`/literatures/${id}/submit`)
}

export function markLiteratureRead(id: Id) {
  return request.post<never, Literature>(`/literatures/${id}/read`)
}

export function semanticSearch(payload: LiteratureSearchPayload) {
  return request.post<never, LiteratureSearchResult>(
    '/search/semantic',
    payload,
  )
}

export function naturalLanguageSearch(payload: LiteratureSearchPayload) {
  return request.post<never, LiteratureSearchResult>(
    '/search/natural-language',
    payload,
  )
}

export function rebuildLiteratureIndex(literatureId: Id) {
  return request.post<never, LiteratureIndexResult>(
    `/ai/literature/${literatureId}/index`,
  )
}

export function generateLiteratureSummary(literatureId: Id) {
  return request.post<never, LiteratureSummaryResult>(
    `/ai/literature/${literatureId}/summary`,
  )
}

export function askLiteratureQuestion(
  literatureId: Id,
  payload: LiteratureQuestionPayload,
) {
  return request.post<never, LiteratureQuestionResult>(
    `/ai/literature/${literatureId}/question`,
    payload,
  )
}

export function listLiteratureQaRecords(params: {
  literatureId?: Id
  page: number
  size: number
}) {
  return request.get<never, LiteratureQuestionResult[]>(
    '/ai/literature/qa-records',
    { params },
  )
}

export function compareLiteratures(payload: LiteratureComparePayload) {
  return request.post<never, LiteratureCompareResult>(
    '/ai/literature/compare',
    payload,
  )
}

export function getReadingHistory(literatureId: Id) {
  return request.get<never, ReadingHistory>(`/reading-history/${literatureId}`)
}

/** 我的书房 · 阅读历史分页列表（GET /reading-history） */
export function listReadingHistory(params: { page: number; size: number }) {
  return request.get<never, PageResult<ReadingHistoryItem>>(
    '/reading-history',
    { params },
  )
}

/** 换取文献原文的 RustFS 预签名下载票据（GET /literatures/{id}/download） */
export function getLiteratureDownload(id: Id) {
  return request.get<never, LiteratureDownloadTicket>(
    `/literatures/${id}/download`,
  )
}

export function saveReadingHistory(
  literatureId: Id,
  payload: ReadingHistoryPayload,
) {
  return request.put<never, ReadingHistory>(
    `/reading-history/${literatureId}`,
    payload,
  )
}

export function listReadingNotes(params: {
  literatureId?: Id
  page: number
  size: number
}) {
  return request.get<never, PageResult<ReadingNote>>('/notes', { params })
}

export function createReadingNote(payload: ReadingNotePayload) {
  return request.post<never, ReadingNote>('/notes', payload)
}

export function updateReadingNote(id: Id, payload: ReadingNotePayload) {
  return request.put<never, ReadingNote>(`/notes/${id}`, payload)
}

export function deleteReadingNote(id: Id) {
  return request.delete<never, void>(`/notes/${id}`)
}

export function favoriteLiterature(literatureId: Id) {
  return request.post<never, Favorite>(`/favorites/${literatureId}`)
}

export function unfavoriteLiterature(literatureId: Id) {
  return request.delete<never, void>(`/favorites/${literatureId}`)
}

export function listFavorites(params: { page: number; size: number }) {
  return request.get<never, PageResult<Favorite>>('/favorites', { params })
}

export function listCategories() {
  return request.get<never, LiteratureCategory[]>('/literature-categories')
}

export function listPendingLiteratures(params: { page: number; size: number }) {
  return request.get<never, PageResult<Literature>>(
    '/admin/literatures/pending',
    {
      params,
    },
  )
}

export function approveLiterature(id: Id) {
  return request.post<never, Literature>(`/admin/literatures/${id}/approve`)
}

export function rejectLiterature(id: Id, reason: string) {
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
