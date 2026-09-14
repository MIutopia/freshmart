<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { CONSOLE_HOME } from '../router/nav'
import { errorMessage } from '../api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const loginName = ref('admin-test-01')
const password = ref('password')
const error = ref('')
const submitting = ref(false)

const canSubmit = computed(() => loginName.value.trim().length > 0 && password.value.length > 0 && !submitting.value)

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  submitting.value = true
  try {
    await auth.login(loginName.value.trim(), password.value)
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect) {
      await router.replace(redirect)
    } else {
      await router.replace(CONSOLE_HOME[auth.primaryConsole()])
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="brand"><span class="mark">叶</span><span>鲜达生鲜</span></div>
      <div class="card">
        <h3>登录</h3>
        <form @submit.prevent="submit">
          <div class="field">
            <label for="loginName">账号</label>
            <input id="loginName" v-model="loginName" autocomplete="username" placeholder="loginName" />
          </div>
          <div class="field">
            <label for="password">密码</label>
            <input id="password" v-model="password" type="password" autocomplete="current-password" placeholder="password" />
          </div>
          <p v-if="error" class="notice error">{{ error }}</p>
          <button class="btn primary" type="submit" :disabled="!canSubmit" style="width: 100%">
            {{ submitting ? '登录中…' : '登录' }}
          </button>
        </form>
        <div class="login-hint">
          本地测试账号（统一密码 <code>password</code>）：<br />
          <code>admin-test-01</code> 管理员 ·
          <code>consumer-test-01</code> 消费者 ·
          <code>merchant-test-01</code> 商家 ·
          <code>rider-test-01</code> 骑手
        </div>
      </div>
    </div>
  </div>
</template>
