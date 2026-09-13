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

export const weighingApi = {
  /**
   * 提交实际称重结果。actualGrams 为实际称重净重克数，填写后会把与预估克数的差额回补到批次库存；
   * 缺省时只结算金额、不动库存。金额差额在平台误差上限内由平台承担，超过上限转人工复核。
   * 每个订单只允许一条称重调整记录。
   */
  submit: (body: { orderId: number; actualGoodsAmount: number; actualGrams?: number; note?: string }) =>
    http.post<WeighingAdjustment>('/weighing-adjustments', { body, idempotent: true })
}
