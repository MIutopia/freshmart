import { http } from './http'

/** 对应后端 MerchantDashboardService.DashboardView */
export interface MerchantDashboard {
  from: string
  to: string
  paidOrderCount: number
  paidAmount: number
  refundedAmount: number
  averageTicket: number
  deliveredOrderCount: number
  lowStock: Array<{ productId: number; productName: string; warehouseId: number; availableGrams: number }>
  topProducts: Array<{ productId: number; productName: string; soldGrams: number; salesAmount: number }>
  delivery: { taskCount: number; deliveredCount: number; waitingAssignmentCount: number; inProgressCount: number }
}

/** 对应后端 MerchantSettlementService.SettlementView */
export interface MerchantSettlement {
  id: number
  merchantId: number
  orderId: number
  grossAmount: number
  commissionBaseAmount: number
  platformPriceSubsidyAmount: number
  commissionRate: number
  commissionAmount: number
  netAmount: number
  status: string
  settledAt: string | null
  settledBy: number | null
  settlementNote: string | null
  createdAt: string
}

/** 对应后端 CatalogService.WarehouseView */
export interface MerchantWarehouse {
  id: number
  merchantId: number
  deliveryZoneId: number
  name: string
  code: string
  address: string
  status: string
}

/** 对应后端 MerchantApplicationService.ApplicationView */
export interface MerchantApplication {
  applicationId: number | null
  merchantId: number | null
  merchantName: string
  applicantUserId: number | null
  businessLicenseUrl: string | null
  status: string
}

export const merchantApi = {
  /** 经营看板；from/to 为必填日期参数 */
  dashboard: (from: string, to: string) => http.get<MerchantDashboard>('/merchant/dashboard', { query: { from, to } }),

  settlements: () => http.get<MerchantSettlement[]>('/merchant/settlements'),

  warehouses: () => http.get<MerchantWarehouse[]>('/merchant/warehouses'),

  /** 入驻申请由消费者提交（后端要求 CONSUMER 角色） */
  submitApplication: (body: { merchantName: string; businessLicenseUrl?: string }) =>
    http.post<MerchantApplication>('/merchant/applications', { body }),

  /** 促销规则：rule 为规则 JSON，startsAt/endsAt 为 ISO LocalDateTime */
  createPromotion: (body: {
    promotionType: string
    name: string
    rule: Record<string, unknown>
    startsAt: string
    endsAt: string
    stackable: boolean
  }) => http.post<{ id: number }>('/merchant/marketing/promotions', { body }),

  createCoupon: (body: {
    name: string
    couponType: string
    thresholdAmount: number
    discountAmount: number
    startsAt: string
    endsAt: string
    totalQuantity: number
  }) => http.post<{ id: number }>('/merchant/marketing/coupons', { body }),

  createFlashSale: (body: {
    productId: number
    salePricePerKg: number
    totalGrams: number
    perUserLimitGrams: number
    startsAt: string
    endsAt: string
  }) => http.post<{ id: number }>('/merchant/marketing/flash-sales', { body })
}
