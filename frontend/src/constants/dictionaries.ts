/**
 * 业务字典：把后端枚举统一映射为中文标签与状态色。
 *
 * 面向的最终用户是普通消费者与商家，他们看不懂 DELIVERED / MANUAL_PROCESS 这类英文枚举，
 * 因此所有页面都不应直接渲染后端状态值，必须经过这里的字典转换。
 * 未收录的值会保留原样显示，便于发现遗漏而不至于把界面变成空白。
 */

export type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

export interface DictEntry {
  label: string
  tagType?: TagType
}

/** 子订单状态 */
export const ORDER_STATUS: Record<string, DictEntry> = {
  PENDING_PAYMENT: { label: '待支付', tagType: 'info' },
  WAITING_PICKING: { label: '待分拣', tagType: 'warning' },
  PICKED: { label: '已拣货', tagType: 'primary' },
  DELIVERING: { label: '配送中', tagType: 'primary' },
  DELIVERED: { label: '已送达', tagType: 'success' },
  REFUNDED: { label: '已退款', tagType: 'danger' },
  CANCELLED: { label: '已取消', tagType: 'info' }
}

/** 配送任务状态 */
export const DELIVERY_TASK_STATUS: Record<string, DictEntry> = {
  WAITING_ASSIGNMENT: { label: '待派单', tagType: 'warning' },
  ASSIGNED: { label: '已派单', tagType: 'info' },
  ACCEPTED: { label: '已接单', tagType: 'primary' },
  PICKED: { label: '已取货', tagType: 'primary' },
  DELIVERING: { label: '配送中', tagType: 'primary' },
  DELIVERED: { label: '已送达', tagType: 'success' },
  EXCEPTION: { label: '异常', tagType: 'danger' }
}

/** 售后单状态 */
export const REFUND_STATUS: Record<string, DictEntry> = {
  PENDING: { label: '待审核', tagType: 'warning' },
  MANUAL_PROCESS: { label: '待人工退款', tagType: 'warning' },
  REFUND_SUCCESS: { label: '退款成功', tagType: 'success' },
  REJECTED: { label: '已驳回', tagType: 'info' },
  REFUND_FAIL: { label: '退款失败', tagType: 'danger' }
}

/** 人工退款工单状态 */
export const MANUAL_REFUND_STATUS: Record<string, DictEntry> = {
  NOT_REQUIRED: { label: '无需人工', tagType: 'info' },
  PENDING: { label: '待处理', tagType: 'warning' },
  COMPLETED: { label: '已完成', tagType: 'success' },
  FAILED: { label: '已失败', tagType: 'danger' }
}

/** 支付单状态 */
export const PAYMENT_STATUS: Record<string, DictEntry> = {
  PENDING_PAYMENT: { label: '待支付', tagType: 'warning' },
  PENDING: { label: '待支付', tagType: 'warning' },
  PAID: { label: '已支付', tagType: 'success' },
  REFUNDED: { label: '已退款', tagType: 'danger' },
  CANCELLED: { label: '已取消', tagType: 'info' }
}

/** 商家结算单状态 */
export const SETTLEMENT_STATUS: Record<string, DictEntry> = {
  PENDING: { label: '待结算', tagType: 'warning' },
  CONFIRMED: { label: '已确认', tagType: 'primary' },
  SETTLED: { label: '已结算', tagType: 'success' },
  REVERSED: { label: '已反冲', tagType: 'danger' }
}

/** 通用的启用 / 停用状态 */
export const ACTIVE_STATUS: Record<string, DictEntry> = {
  ACTIVE: { label: '启用', tagType: 'success' },
  INACTIVE: { label: '停用', tagType: 'info' }
}

/** 商家与商品状态 */
export const MERCHANT_STATUS: Record<string, DictEntry> = {
  PENDING: { label: '待审核', tagType: 'warning' },
  APPROVED: { label: '已通过', tagType: 'success' },
  REJECTED: { label: '已驳回', tagType: 'danger' },
  ACTIVE: { label: '营业中', tagType: 'success' },
  INACTIVE: { label: '已停业', tagType: 'info' }
}

export const PRODUCT_STATUS: Record<string, DictEntry> = {
  DRAFT: { label: '草稿', tagType: 'info' },
  ACTIVE: { label: '已上架', tagType: 'success' },
  INACTIVE: { label: '已下架', tagType: 'info' }
}

/** 商品品类，同时决定售后窗口 */
export const PRODUCT_SCOPE: Record<string, DictEntry> = {
  FRUIT: { label: '水果', tagType: 'success' },
  VEGETABLE: { label: '蔬菜', tagType: 'primary' },
  OTHER: { label: '其他', tagType: 'info' }
}

/** 账号角色 */
export const ROLE: Record<string, DictEntry> = {
  ADMIN: { label: '平台管理员', tagType: 'danger' },
  OPERATIONS: { label: '运营', tagType: 'warning' },
  FINANCE: { label: '财务', tagType: 'primary' },
  MERCHANT: { label: '商家', tagType: 'success' },
  CONSUMER: { label: '消费者', tagType: 'info' },
  RIDER: { label: '配送员', tagType: 'primary' }
}

/** 平台规则取值类型 */
export const VALUE_TYPE: Record<string, DictEntry> = {
  DECIMAL: { label: '小数', tagType: 'info' },
  INTEGER: { label: '整数', tagType: 'info' },
  BOOLEAN: { label: '开关', tagType: 'info' },
  STRING: { label: '文本', tagType: 'info' }
}

/** 售后问题类型 */
export const REFUND_ISSUE_TYPE: Record<string, DictEntry> = {
  OUT_OF_STOCK: { label: '缺货', tagType: 'warning' },
  QUALITY: { label: '品质问题', tagType: 'danger' }
}

/** 售后库存处置 */
export const INVENTORY_DISPOSITION: Record<string, DictEntry> = {
  PENDING_INSPECTION: { label: '待检验', tagType: 'warning' },
  RESTOCKED: { label: '已回库', tagType: 'success' },
  DISCARDED: { label: '已报损', tagType: 'danger' }
}

/** 称重结算动作 */
export const WEIGHING_ACTION: Record<string, DictEntry> = {
  REFUND_USER: { label: '退回用户差额', tagType: 'warning' },
  PLATFORM_ABSORB: { label: '平台承担', tagType: 'info' },
  REQUIRES_REVIEW: { label: '转人工复核', tagType: 'danger' }
}

/** 支付方式 */
export const PAYMENT_PROVIDER: Record<string, DictEntry> = {
  BALANCE: { label: '余额支付', tagType: 'success' },
  PERSONAL_WECHAT_QR: { label: '个人微信收款码', tagType: 'primary' }
}

/** 对账差异处理状态：流转为 UNHANDLED → HANDLING → HANDLED，也可暂存为 PENDING_SHELVE */
export const RECONCILIATION_STATUS: Record<string, DictEntry> = {
  UNHANDLED: { label: '未处理', tagType: 'warning' },
  HANDLING: { label: '处理中', tagType: 'primary' },
  PENDING_SHELVE: { label: '已暂存', tagType: 'info' },
  HANDLED: { label: '已处理', tagType: 'success' }
}

/** 节气卡片任务状态 */
export const HOLIDAY_TASK_STATUS: Record<string, DictEntry> = {
  PENDING: { label: '待执行', tagType: 'warning' },
  RUNNING: { label: '执行中', tagType: 'primary' },
  COMPLETED: { label: '已完成', tagType: 'success' },
  FAILED: { label: '执行失败', tagType: 'danger' }
}

/** 节气卡片投递状态 */
export const HOLIDAY_DELIVERY_STATUS: Record<string, DictEntry> = {
  PENDING: { label: '待投递', tagType: 'warning' },
  PROCESSING: { label: '投递中', tagType: 'primary' },
  SENT: { label: '已送达', tagType: 'success' },
  FAILED: { label: '投递失败', tagType: 'danger' }
}

/** 取中文标签；未收录时回退为原值，便于发现遗漏 */
export function labelOf(dict: Record<string, DictEntry>, value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const key = String(value)
  return dict[key]?.label ?? key
}

/** 取标签配色；未收录时用中性色 */
export function tagTypeOf(dict: Record<string, DictEntry>, value: unknown): TagType {
  if (value === null || value === undefined || value === '') return 'info'
  return dict[String(value)]?.tagType ?? 'info'
}

/** 生成下拉选项，避免各页面手写重复的中文映射 */
export function optionsOf(dict: Record<string, DictEntry>): Array<{ label: string; value: string }> {
  return Object.entries(dict).map(([value, entry]) => ({ label: entry.label, value }))
}
