<script setup lang="ts">
import { ref } from 'vue'

const activeRole = ref('用户端')
const roles = ['用户端', '商家端', '配送端', '管理后台']
const modules: Record<string, string[]> = {
  用户端: ['首页与搜索', '购物车结算', '订单与售后', 'AI 导购'],
  商家端: ['店铺资料', '商品与库存', '订单处理', '经营分析'],
  配送端: ['接单大厅', '配送任务', '路线与签收', '异常上报'],
  管理后台: ['用户与权限', '平台商品', '交易履约', 'AI 审计']
}
</script>

<template>
  <main class="shell">
    <header class="topbar"><div class="brand"><span class="mark">叶</span><span>鲜达生鲜</span></div><span class="badge">架构骨架 v0.1.0</span></header>
    <section class="intro"><p class="eyebrow">FRESH DELIVERY PLATFORM</p><h1>从产地到餐桌的可追踪配送商城</h1><p>Spring Boot + Vue 四端协同，AI 负责推荐、客服与运营洞察，关键交易由业务规则兜底。</p></section>
    <nav class="role-tabs" aria-label="业务端选择"><button v-for="role in roles" :key="role" :class="{ active: activeRole === role }" @click="activeRole = role">{{ role }}</button></nav>
    <section class="module-grid"><article v-for="module in modules[activeRole]" :key="module" class="module-card"><span class="dot"></span><h2>{{ module }}</h2><p>待确认业务规则后进入详细设计与接口实现。</p></article></section>
    <footer><span>AI Gateway 独立隔离</span><span>订单状态全程留痕</span><span>库存与支付幂等</span></footer>
  </main>
</template>
