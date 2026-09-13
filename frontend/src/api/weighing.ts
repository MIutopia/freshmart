import { http } from './http'

/** 对应后端 WeighingAdjustmentService.AdjustmentView */
export interface WeighingAdjustment {
  adjustmentId: string
  orderId: number
  merchantId: number
  prepaidGoodsAmount: number
  actualGoodsAmount: number
  actualGrams: number | null
  /** 已回补到批次库存的克数差额：正数表示补扣，负数表示退回 */
  inventoryAdjustGrams: number
  action: string
  refundAmount: number
  absorbedAmount: number
  createdAt: string
}

/** 对应后端 WeighingAdjustmentService.WeighingSheetItem */
export interface WeighingSheetItem {
  orderItemId: number
  productName: string
  prepaidGrams: number
  prepaidGoodsAmount: number
  actualGrams: number | null
  actualGoodsAmount: number | null
  weighedAt: string | null
}

/** 对应后端 WeighingAdjustmentService.WeighingSheetView */
export interface WeighingSheet {
  orderId: number
  status: string
  prepaidGoodsAmount: number
  items: WeighingSheetItem[]
}

export const weighingApi = {
  /** 逐项称重前取订单项与预估克数 */
  sheet: (orderId: number) => http.get<WeighingSheet>(`/merchant/orders/${orderId}/weighing-sheet`),

  /**
   * 提交称重结果。
   * 逐项模式（items）：整单实际金额与克数由各项汇总，并按订单项各自回补批次库存；
   * 整单模式（actualGrams/actualGoodsAmount）：按订单整体比例分摊，缺省克数时只结算金额。
   * 金额差额在平台误差上限内由平台承担，低于预估退回用户；超过上限转人工复核。
   */
  submit: (body: {
    orderId: number
    actualGoodsAmount?: number
    actualGrams?: number
    items?: Array<{ orderItemId: number; actualGrams: number; actualGoodsAmount: number }>
    note?: string
  }) => http.post<WeighingAdjustment>('/weighing-adjustments', { body, idempotent: true })
}
