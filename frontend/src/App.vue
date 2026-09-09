<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const roles = [
  { label: '用户端', path: '/app' },
  { label: '商家端', path: '/merchant' },
  { label: '配送端', path: '/delivery' },
  { label: '管理后台', path: '/admin' }
]
const roleByPath = Object.fromEntries(roles.map((role) => [role.path, role.label]))
const activeRole = ref(roleByPath[window.location.pathname] ?? '用户端')
const modules: Record<string, string[]> = {
  用户端: ['账号密码登录', '余额 / 微信扫码支付', '订单与售后', 'AI 导购与订单查询'],
  商家端: ['入驻资料与独立店铺', '商品与库存', '订单处理', '佣金结算'],
  配送端: ['平台派单', '接单超时', '配送签收', '骑手绩效'],
  管理后台: ['商家审核与佣金', '平台规则配置', '优惠 / 会员 / 积分 / 秒杀', '客服售后审核']
}

function navigateRole(role: { label: string; path: string }) {
  activeRole.value = role.label
  window.history.pushState({}, '', role.path)
}

function syncRole() {
  activeRole.value = roleByPath[window.location.pathname] ?? '用户端'
}

onMounted(() => window.addEventListener('popstate', syncRole))
onUnmounted(() => window.removeEventListener('popstate', syncRole))
</script>

<template>
  <main class="shell">
    <header class="topbar"><div class="brand"><span class="mark">叶</span><span>鲜达生鲜</span></div><span class="badge">网页端 MVP · {{ roles.find((role) => role.label === activeRole)?.path }}</span></header>
    <section class="intro"><p class="eyebrow">FRESH DELIVERY PLATFORM</p><h1>从产地到餐桌的可追踪配送商城</h1><p>多商家入驻，平台统一配送；余额与微信扫码支付，AI 辅助导购和订单查询。</p></section>
    <nav class="role-tabs" aria-label="业务端选择"><button v-for="role in roles" :key="role.path" :class="{ active: activeRole === role.label }" @click="navigateRole(role)">{{ role.label }}</button></nav>
    <section class="module-grid"><article v-for="module in modules[activeRole]" :key="module" class="module-card"><span class="dot"></span><h2>{{ module }}</h2><p>首期网页端功能边界，接口与权限按技术文档实现。</p></article></section>
    <footer><span>AI Gateway 独立隔离</span><span>订单状态全程留痕</span><span>库存与支付幂等</span></footer>
  </main>
</template>
