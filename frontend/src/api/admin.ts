import { http } from './http'
import type { MerchantApplication, MerchantSettlement } from './merchant'
import type { ReconciliationDifferenceView } from './trade'
import type { RefundSuggestionView, RefundView } from './afterSale'

/** 对应后端 PlatformRuleService.RuleView */
export interface PlatformRule {
  ruleKey: string
  ruleValue: string
  valueType: string
  description: string
  updatedBy: number | null
  updatedAt: string
}

/** 对应后端 DeliveryService.DeliveryZoneView */
export interface DeliveryZoneView {
  id: number
  name: string
  areaCode: string
  boundaryJson: string | null
  status: string
}

/** 对应后端 BatchPromotionService 的两个视图 */
export interface BatchPromotionCandidate {
  batchId: number
  productId: number
  productName: string
  merchantId: number
  warehouseId: number
  batchNo: string
  availableGrams: number
  reservedGrams: number
  expiresOn: string
}

export interface BatchPromotionView {
  id: number
  batchId: number
  markdownRate: number
  startsAt: string
  endsAt: string
  status: string
}

/** 对应后端 MerchantOperationsController.PlatformCostView */
export interface PlatformCostView {
  feeType: string
  direction: string
  amount: number
  entryCount: number
}

export const adminApi = {
  dashboard: () => http.get<Record<string, unknown>>('/admin/dashboard'),

  // 平台规则
  platformRules: () => http.get<PlatformRule[]>('/admin/platform-rules'),
  updatePlatformRule: (ruleKey: string, value: string) =>
    http.put<PlatformRule>(`/admin/platform-rules/${ruleKey}`, { body: { value } }),

  // 商家审核
  merchantApplications: (status?: string) =>
    http.get<MerchantApplication[]>('/admin/merchants/applications', { query: { status } }),
  reviewMerchant: (merchantId: number, body: { approved: boolean; reviewNote?: string }) =>
    http.put<MerchantApplication>(`/admin/merchants/${merchantId}/review`, { body }),

  // 商品分类与配送区域
  createCategory: (body: { parentId?: number; name: string; sortOrder: number }) =>
    http.post<{ id: number }>('/admin/categories', { body }),
  deliveryZones: () => http.get<DeliveryZoneView[]>('/admin/delivery-zones'),
  createDeliveryZone: (body: { name: string; areaCode: string; boundaryJson?: string }) =>
    http.post<{ id: number }>('/admin/delivery-zones', { body }),
  updateDeliveryZone: (zoneId: number, body: { name: string; boundaryJson?: string; status?: string }) =>
    http.put<void>(`/admin/delivery-zones/${zoneId}`, { body }),

  // 派单
  assignDeliveryTask: (taskId: number, body: { riderUserId: number }) =>
    http.post<void>(`/admin/delivery-tasks/${taskId}/assign`, { body }),

  // 佣金与结算
  commissions: () => http.get<MerchantSettlement[]>('/admin/commissions'),
  generateCommissions: () => http.post<MerchantSettlement[]>('/admin/commissions/generate', { idempotent: true }),
  confirmSettlement: (settlementId: number, body: { note: string }) =>
    http.put<MerchantSettlement>(`/admin/commissions/${settlementId}/confirm`, { body }),

  // 平台成本
  platformCosts: (from: string, to: string) =>
    http.get<PlatformCostView[]>('/admin/platform-costs', { query: { from, to } }),

  // 支付核验与对账差异
  verifyProof: (paymentNo: string, body: { approved: boolean; note: string }) =>
    http.post<void>(`/admin/payments/${paymentNo}/verify-proof`, { body }),
  confirmPersonalWechatQr: (tradeNo: string) =>
    http.post<void>(`/admin/payments/${tradeNo}/confirm-personal-wechat-qr`, { idempotent: true }),
  importBill: (body: { fileName: string; entries: Array<Record<string, unknown>> }) =>
    http.post<Record<string, unknown>>('/admin/payments/bill-imports', { body, idempotent: true }),
  reconciliationDifferences: (status?: string) =>
    http.get<ReconciliationDifferenceView[]>('/admin/payments/reconciliation-differences', { query: { status } }),
  claimDifference: (differenceId: number, body: { note: string }) =>
    http.post<void>(`/admin/payments/reconciliation-differences/${differenceId}/claim`, { body }),
  shelveDifference: (differenceId: number, body: { note: string }) =>
    http.post<void>(`/admin/payments/reconciliation-differences/${differenceId}/shelve`, { body }),
  resolveDifference: (differenceId: number, body: { resolution: string; note: string }) =>
    http.post<void>(`/admin/payments/reconciliation-differences/${differenceId}/resolve`, { body }),

  // 售后审核与人工退款
  refunds: (status?: string) => http.get<RefundView[]>('/admin/refunds', { query: { status } }),
  reviewRefund: (refundId: number, body: { approved: boolean; reviewNote: string }) =>
    http.put<RefundView>(`/admin/refunds/${refundId}/review`, { body }),
  completeRefund: (refundNo: string) =>
    http.post<RefundView>(`/admin/refunds/${refundNo}/manual-complete`, { idempotent: true }),
  failRefund: (refundNo: string, body: { reason: string }) =>
    http.post<RefundView>(`/admin/refunds/${refundNo}/manual-fail`, { body }),
  retryRefund: (refundNo: string, body: { reason: string }) =>
    http.post<RefundView>(`/admin/refunds/${refundNo}/manual-retry`, { body }),
  processInventoryDisposition: (refundNo: string, body: { disposition: string; note: string }) =>
    http.put<Record<string, unknown>>(`/admin/refunds/${refundNo}/inventory-disposition`, { body }),
  refundAiSuggestion: (refundNo: string) =>
    http.post<RefundSuggestionView>(`/admin/refunds/${refundNo}/ai-review-suggestion`, { idempotent: true }),

  // 临期批次促销
  batchPromotionCandidates: () =>
    http.get<BatchPromotionCandidate[]>('/admin/marketing/batch-promotions/candidates'),
  createBatchPromotion: (body: { batchId: number; markdownRate: number; startsAt: string; endsAt: string }) =>
    http.post<BatchPromotionView>('/admin/marketing/batch-promotions', { body }),

  // 节气卡片任务
  holidayCardTasks: () => http.get<Record<string, unknown>[]>('/admin/notification/holiday-card-tasks'),
  createHolidayCardTask: (body: Record<string, unknown>) =>
    http.post<Record<string, unknown>>('/admin/notification/holiday-card-tasks', { body }),
  runHolidayCardTask: (taskId: number) =>
    http.post<Record<string, unknown>>(`/admin/notification/holiday-card-tasks/${taskId}/run`, { idempotent: true }),
  generateHolidayCard: (body: Record<string, unknown>) =>
    http.post<Record<string, unknown>>('/admin/ai/holiday-cards', { body }),

  // 站内消息
  sendInboxMessage: (body: { userId: number; title: string; content: string }) =>
    http.post<void>('/admin/inbox/messages', { body })
}
