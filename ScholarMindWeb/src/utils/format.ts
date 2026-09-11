const categoryLabels: Record<string, string> = {
  'Digital Library': '数字图书馆',
  'Information Retrieval': '信息检索',
  'Artificial Intelligence': '人工智能',
  'Recommendation System': '推荐系统',
}

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '审核中',
  PUBLISHED: '已发布',
  REJECTED: '已退回',
}

export function displayCategoryName(name?: string | null) {
  if (!name) return '未分类'
  return categoryLabels[name] ?? name
}

export function displayStatusName(status?: string | null) {
  if (!status) return '未知状态'
  return statusLabels[status] ?? status
}

export function localizeDimensionName(name?: string | null) {
  return displayCategoryName(name)
}
