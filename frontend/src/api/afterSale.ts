import { http } from './http'

/** 对应后端 RefundController.RefundRequest */
export interface RefundRequest {
  orderId: number
  issueType: string
  /** 问题描述，后端字段名为 description */
  description: string
  evidenceImages: string[]
}

/** 对应后端 RefundService.RefundView */
export interface RefundView {
  id: number
  refundNo: string
  orderId: number
  issueType: string
  amount: number
  status: string
  deliveredAt: string | null
}

/** 对应后端 RefundService.InventoryDispositionView */
export interface InventoryDispositionView {
  refundNo: string
  orderId: number
  disposition: string
  reason: string
  processedBy: number
  processedAt: string
  processingNote: string
}

/** 对应后端 RefundAiReviewService.SuggestionView */
export interface RefundSuggestionView {
  refundNo: string
  issueType: string
  evidenceCount: number
  suggestion: string
  disclaimer: string
}

export const refundApi = {
  /** 申请售后；必须携带 Idempotency-Key */
  create: (body: RefundRequest) => http.post<RefundView>('/refunds', { body, idempotent: true }),

  listMine: () => http.get<RefundView[]>('/refunds')
}

export const mediaApi = {
  /** 上传 JPEG/PNG/WebP/MP4/MOV，单文件不超过 30 MB，返回受保护 URL */
  upload: async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    const token = localStorage.getItem('freshmart.token')
    const response = await fetch('/api/media/upload', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      body: form
    })
    if (!response.ok) {
      throw new Error(`上传失败（HTTP ${response.status}）`)
    }
    return (await response.json()) as { url: string; contentType: string; sizeBytes: number }
  }
}

export interface InboxMessage {
  id: number
  title: string
  content: string
  cardSvgUrl?: string | null
  cardSvgContent?: string | null
  read: boolean
  createdAt: string
  [key: string]: unknown
}

export const inboxApi = {
  list: () => http.get<InboxMessage[]>('/inbox/messages'),
  markRead: (messageId: number) => http.post<void>(`/inbox/messages/${messageId}/read`)
}

export const notificationPreferenceApi = {
  get: () => http.get<{ seasonalCardEnabled: boolean }>('/notification-preferences'),
  update: (body: { seasonalCardEnabled: boolean }) => http.put<void>('/notification-preferences', { body })
}

/** 对应后端 HolidayCardService.PreviewView */
export interface HolidayCardPreview {
  holidayKey: string
  greeting: string
  /** 已由后端转义，可安全内联渲染 */
  svg: string
}

export const holidayCardApi = {
  /** 用户侧预览：只渲染卡片外观，不产生站内消息、不写投递记录 */
  preview: (holidayKey?: string, greeting?: string) =>
    http.get<HolidayCardPreview>('/holiday-cards/preview', { query: { holidayKey, greeting } })
}
