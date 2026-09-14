import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, type Profile, type Role } from '../api/auth'

const TOKEN_KEY = 'freshmart.token'

/** 四类业务端；后台由 ADMIN / OPERATIONS / FINANCE 共用 */
export type ConsoleKey = 'app' | 'merchant' | 'delivery' | 'admin'

export const CONSOLE_ROLES: Record<ConsoleKey, Role[]> = {
  app: ['CONSUMER'],
  merchant: ['MERCHANT'],
  delivery: ['RIDER'],
  admin: ['ADMIN', 'OPERATIONS', 'FINANCE']
}

export const CONSOLE_LABEL: Record<ConsoleKey, string> = {
  app: '用户端',
  merchant: '商家端',
  delivery: '配送端',
  admin: '管理后台'
}

/** 有多重身份时，登录后优先进入的端 */
const CONSOLE_PRIORITY: ConsoleKey[] = ['admin', 'merchant', 'delivery', 'app']

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const profile = ref<Profile | null>(null)
  const restored = ref(false)

  const roles = computed<Role[]>(() => profile.value?.roles ?? [])
  const isAuthenticated = computed(() => Boolean(token.value && profile.value))
  const loginName = computed(() => profile.value?.loginName ?? '')

  function setToken(value: string) {
    token.value = value
    if (value) localStorage.setItem(TOKEN_KEY, value)
    else localStorage.removeItem(TOKEN_KEY)
  }

  function hasRole(...wanted: Role[]) {
    return wanted.some((role) => roles.value.includes(role))
  }

  function allowedConsoles(): ConsoleKey[] {
    return CONSOLE_PRIORITY.filter((key) => hasRole(...CONSOLE_ROLES[key]))
  }

  function primaryConsole(): ConsoleKey {
    return allowedConsoles()[0] ?? 'app'
  }

  function canAccess(key: ConsoleKey) {
    return hasRole(...CONSOLE_ROLES[key])
  }

  function clear() {
    setToken('')
    profile.value = null
  }

  async function login(name: string, password: string) {
    const result = await authApi.login(name, password)
    setToken(result.accessToken)
    profile.value = { userId: result.userId, loginName: result.loginName, roles: result.roles }
    restored.value = true
    return result
  }

  /** 刷新页面后用已存 token 还原登录态；失败则清空 */
  async function restore() {
    if (restored.value) return
    if (!token.value) {
      restored.value = true
      return
    }
    try {
      profile.value = await authApi.me()
    } catch {
      clear()
    } finally {
      restored.value = true
    }
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } catch {
      // 服务端吊销失败不阻塞本地登出
    } finally {
      clear()
    }
  }

  return {
    token,
    profile,
    restored,
    roles,
    isAuthenticated,
    loginName,
    setToken,
    hasRole,
    allowedConsoles,
    primaryConsole,
    canAccess,
    clear,
    login,
    restore,
    logout
  }
})
