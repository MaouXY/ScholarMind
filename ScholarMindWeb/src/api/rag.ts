import type {
  Id,
  KnowledgeDocument,
  KnowledgeDocumentPayload,
  KnowledgePage,
} from '@/types/rag'

import request from '@/utils/request'

export function listKnowledgeDocuments(params: { page: number; size: number }) {
  return request.get<never, KnowledgePage>('/admin/rag/documents', { params })
}

export function createKnowledgeDocument(payload: KnowledgeDocumentPayload) {
  return request.post<never, KnowledgeDocument>('/admin/rag/documents', payload)
}

export function updateKnowledgeDocument(
  id: Id,
  payload: KnowledgeDocumentPayload,
) {
  return request.put<never, KnowledgeDocument>(`/admin/rag/documents/${id}`, payload)
}

export function deleteKnowledgeDocument(id: Id) {
  return request.delete<never, void>(`/admin/rag/documents/${id}`)
}

export function rebuildKnowledgeDocument(id: Id) {
  return request.post<never, number>(`/admin/rag/documents/${id}/rebuild`)
}
