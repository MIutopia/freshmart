import { http } from './http'

/** 对应后端 OrderQueryService.OrderView */
export interface OrderView {
  id: number
  orderNo: string
  userId: number
  merchantId: number
  warehouseId: number
  deliveryZoneId: number
  status: string
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payableAmount: number
  actualGoodsAmount: number | null
  platformAbsorbedAmount: number | null
  createdAt: string
}

/** 对应后端 TradeController.CreateTradeRequest；商家与仓库由后端按商品推导 */
export interface CreateTradeRequest {
  deliveryZoneId: number
  /** 下单地址快照，后端原样保存 */
  addressSnapshot: Record<string, unknown>
  lines: Array<{ productId: number; weightGrams: number }>
  couponIds?: number[]
  pointsToRedeem?: number
}

/** 对应后端 TradeService.TradeView */
export interface TradeView {
  id: number
  tradeNo: string
  userId: number
  status: string
  goodsAmount: number
  freightAmount: number
  discountAmount: number
  payableAmount: number
  reservationExpiresAt: string
  orderNos: string[]
}

/** 对应后端 TradeService.PaymentView */
export interface PaymentView {
  paymentNo: string
  status: string
  amount: number
  codeUrl: string | null
  remarkText: string | null
}

/** 对应后端 TradeService.ReconciliationDifferenceView */
export interface ReconciliationDifferenceView {
  id: number
  paymentNo: string | null
  billEntryId: number | null
  differenceType: string
  description: string
  status: string
  claimedBy: number | null
  claimedAt: string | null
  resolvedBy: number | null
  resolvedAt: string | null
  resolution: string | null
  resolutionNote: string | null
  createdAt: string
}

export const tradeApi = {
  /** 创建交易单；必须携带 Idempotency-Key */
  create: (body: CreateTradeRequest) => http.post<TradeView>('/trades', { body, idempotent: true }),

  paymentCode: (tradeNo: string) => http.get<PaymentView>(`/trades/${tradeNo}/payment-code`)
}

export const paymentApi = {
  prepay: (tradeNo: string) => http.post<PaymentView>(`/payments/${tradeNo}/prepay`, { idempotent: true }),

  confirmBalance: (tradeNo: string) => http.post<TradeView>(`/payments/${tradeNo}/confirm-balance`, { idempotent: true }),

  /** 提交个人收款码转账凭证；remarkText 必填，用于账单匹配 */
  submitProof: (tradeNo: string, body: { proofUrl: string; remarkText: string }) =>
    http.post<PaymentView>(`/payments/${tradeNo}/proof`, { body })
}

export const orderApi = {
  listMine: () => http.get<OrderView[]>('/orders'),
  detail: (orderId: number) => http.get<OrderView>(`/orders/${orderId}`),
  receipt: (orderId: number) => http.get<Record<string, unknown>>(`/orders/${orderId}/receipt`),
  traceability: (orderId: number) => http.get<Record<string, unknown>[]>(`/orders/${orderId}/traceability`)
}
