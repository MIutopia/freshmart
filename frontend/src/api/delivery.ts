import { http } from './http'

/** 对应后端 DeliveryService.DeliveryTaskView */
export interface DeliveryTask {
  id: number
  orderId: number
  merchantId: number
  warehouseId: number
  deliveryZoneId: number
  status: string
  acceptDeadlineAt: string | null
  assignedAt: string | null
  acceptedAt: string | null
  pickedAt: string | null
  deliveredAt: string | null
  proofUrl: string | null
  exceptionNote: string | null
  /** 被指派的骑手用户 ID，未派单时为 null */
  riderUserId: number | null
}

/** 对应后端 DeliveryService.RiderPerformanceView */
export interface RiderPerformance {
  statDate: string
  assignedCount: number
  acceptedCount: number
  deliveredCount: number
  timeoutCount: number
  onTimeRate: number
}

export const deliveryApi = {
  tasks: () => http.get<DeliveryTask[]>('/delivery/tasks'),

  accept: (taskId: number) => http.put<void>(`/delivery/tasks/${taskId}/accept`, { idempotent: true }),

  pick: (taskId: number) => http.put<void>(`/delivery/tasks/${taskId}/pick`, { idempotent: true }),

  /** 送达必须提交凭证 URL */
  deliver: (taskId: number, body: { proofUrl: string }) =>
    http.put<void>(`/delivery/tasks/${taskId}/deliver`, { body, idempotent: true }),

  /** 绩效按日期区间查询；from/to 必填 */
  performance: (from: string, to: string) =>
    http.get<RiderPerformance[]>('/delivery/performance', { query: { from, to } })
}
