import { http } from './http'

export interface AiMessageResponse {
  reply?: string
  answer?: string
  [key: string]: unknown
}

export const aiApi = {
  /** 消费者 AI 导购与本人订单查询，工具范围由后端固定 */
  assistant: (body: { message: string; conversationId?: string }) =>
    http.post<AiMessageResponse>('/ai/assistant/messages', { body })
}
