<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'

const data = ref<Record<string, unknown> | null>(null)
const loading = ref(false)

const METRIC_LABELS: Record<string, string> = {
  todayOrderCount: '今日订单',
  todaySalesAmount: '今日销售额',
  refundOrderCount: '退款单总数',
  userCount: '注册用户',
  activeMerchantCount: '在营商家',
  activeProductCount: '上架商品',
  deliveryTaskCount: '配送任务总数',
  deliveredTaskCount: '已送达任务'
}

const metrics = computed(() => {
  if (!data.value) return []
  return Object.entries(METRIC_LABELS).map(([key, label]) => ({
    label,
    value: key.includes('Amount') ? `¥${data.value?.[key] ?? 0}` : (data.value?.[key] ?? 0)
  }))
})

async function load() {
  loading.value = true
  try {
    data.value = await adminApi.dashboard()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-dashboard">
    <header class="page-head">
      <h2>平台概览</h2>
      <span class="sub">跨卫星库只读聚合；统计口径固定，避免与商家看板漂移</span>
    </header>

    <div class="admin-dashboard__toolbar">
      <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-skeleton v-if="loading && !data" :rows="4" animated class="admin-dashboard__skeleton" />

    <div v-else class="admin-dashboard__metrics">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card">
        <span class="metric-card__label">{{ metric.label }}</span>
        <strong class="metric-card__value">{{ metric.value }}</strong>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.admin-dashboard {
  @include page-shell;

  &__toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
  }

  &__skeleton {
    padding: 20px;
    @include panel;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
  }
}

.metric-card {
  padding: 16px 18px;
  @include panel;
  @include card-hover;

  &__label {
    font-size: 12px;
    color: $text-muted;
  }

  &__value {
    display: block;
    margin-top: 8px;
    font-size: 22px;
    font-weight: 700;
    color: $text-primary;
  }
}

@media (max-width: 1280px) {
  .admin-dashboard__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .admin-dashboard__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .admin-dashboard__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
