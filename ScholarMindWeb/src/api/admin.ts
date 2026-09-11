import type { AdminDashboard } from '@/types/admin'

import request from '@/utils/request'

export function getAdminDashboard(params: { days?: number; limit?: number }) {
  return request.get<never, AdminDashboard>('/admin/statistics/dashboard', {
    params,
  })
}
