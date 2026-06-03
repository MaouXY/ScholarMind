import type {
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
  id: number,
  payload: KnowledgeDocumentPayload,
) {
  return request.put<never, KnowledgeDocument>(`/admin/rag/documents/${id}`, payload)
}

export function deleteKnowledgeDocument(id: number) {
  return request.delete<never, void>(`/admin/rag/documents/${id}`)
}

export function rebuildKnowledgeDocument(id: number) {
  return request.post<never, number>(`/admin/rag/documents/${id}/rebuild`)
}
