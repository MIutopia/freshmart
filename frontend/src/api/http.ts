/**
 * 统一请求层。
 *
 * 后端返回裸 JSON + HTTP 状态码（没有 code/message/data 包装），
 * 因此这里以 HTTP 状态判断成败，并从 Spring 的错误体里提取可读文案。
 */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export interface RequestOptions {
  query?: Record<string, string | number | boolean | undefined | null>
  body?: unknown
  /** 写操作自动附带 Idempotency-Key，避免重复下单等重放问题 */
  idempotent?: boolean
}

const BASE_URL = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api'

let tokenGetter: () => string | null = () => null
let unauthorizedHandler: () => void = () => {}

export function configureHttp(options: {
  getToken: () => string | null
  onUnauthorized: () => void
}) {
  tokenGetter = options.getToken
  unauthorizedHandler = options.onUnauthorized
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function pickMessage(payload: unknown, fallback: string): string {
  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>
    for (const key of ['detail', 'message', 'error', 'title']) {
      const value = record[key]
      if (typeof value === 'string' && value.trim()) return value
    }
  }
  return fallback
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`, window.location.origin)

  if (options.query) {
    for (const [key, value] of Object.entries(options.query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value))
      }
    }
  }

  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = tokenGetter()
  if (token) headers.Authorization = `Bearer ${token}`

  let body: string | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  if (options.idempotent) {
    headers['Idempotency-Key'] = crypto.randomUUID()
  }

  let response: Response
  try {
    response = await fetch(url.toString(), { method, headers, body })
  } catch {
    throw new ApiError(0, '无法连接后端服务，请确认服务已启动')
  }

  if (response.status === 401) {
    unauthorizedHandler()
    throw new ApiError(401, '登录状态已失效，请重新登录')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const payload = text ? parseJson(text) : null

  if (!response.ok) {
    throw new ApiError(response.status, pickMessage(payload, `请求失败（HTTP ${response.status}）`))
  }

  return payload as T
}

export const http = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, options),
  post: <T>(path: string, options?: RequestOptions) => request<T>('POST', path, options),
  put: <T>(path: string, options?: RequestOptions) => request<T>('PUT', path, options),
  delete: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options)
}

/** 把异常统一转成可展示的文案 */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '发生未知错误'
}
