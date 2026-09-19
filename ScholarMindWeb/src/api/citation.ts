import type {
  CitationPayload,
  CitationQuery,
  CitationRecord,
  CitationStyleItem,
} from '@/types/citation'
import type { Id, PageResult } from '@/types/literature'

import request from '@/utils/request'

/** 读取某篇文献即时生成的三种引用格式（GB/T 7714、APA、MLA） */
export function listLiteratureCitations(literatureId: Id) {
  return request.get<never, CitationStyleItem[]>(
    `/literatures/${literatureId}/citations`,
  )
}

/** 分页查询当前用户已收藏的引用记录，可按文献过滤 */
export function listCitations(params: CitationQuery) {
  return request.get<never, PageResult<CitationRecord>>('/citations', {
    params,
  })
}

/** 收藏一条引用（同一文献同一格式重复收藏时后端返回 14402，拦截器自动提示） */
export function createCitation(payload: CitationPayload) {
  return request.post<never, CitationRecord>('/citations', payload)
}

/** 取消收藏引用 */
export function deleteCitation(id: Id) {
  return request.delete<never, void>(`/citations/${id}`)
}
