const categoryLabels: Record<string, string> = {
  'Digital Library': '数字图书馆',
  'Information Retrieval': '信息检索',
  'Artificial Intelligence': '人工智能',
  'Recommendation System': '推荐系统',
  'Data Mining': '数据挖掘',
  'Knowledge Graph': '知识图谱',
  'Academic Writing': '学术写作',
  'Semantic Retrieval': '语义检索',
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

const roleLabels: Record<string, string> = {
  STUDENT: '用户端',
  ADMIN: '管理端',
}

export function displayRoleName(role?: string | null) {
  if (!role) return '未知角色'
  return roleLabels[role] ?? role
}

export function localizeDimensionName(name?: string | null) {
  return displayCategoryName(name)
}

export function displayLanguage(value?: string | null) {
  if (value?.startsWith('zh')) return '中文'
  if (value?.startsWith('en')) return '英文'
  return value || '未标注语种'
}
