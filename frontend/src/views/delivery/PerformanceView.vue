<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { deliveryApi, type RiderPerformance } from '../../api/delivery'
import { errorMessage } from '../../api/http'

/** 后端 /api/delivery/performance 的 from/to 为必填日期参数 */
function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date(end.getTime() - 29 * 24 * 60 * 60 * 1000)
  const format = (date: Date) => date.toISOString().slice(0, 10)
  return [format(start), format(end)]
}

const range = ref<[string, string]>(defaultRange())
const rows = ref<RiderPerformance[]>([])
const loading = ref(false)

const summary = computed(() => {
  const total = rows.value.reduce(
    (acc, row) => {
      acc.assigned += row.assignedCount
      acc.accepted += row.acceptedCount
      acc.delivered += row.deliveredCount
      acc.timeout += row.timeoutCount
      return acc
    },
    { assigned: 0, accepted: 0, delivered: 0, timeout: 0 }
  )
  return total
})

async function load() {
  if (!range.value?.[0] || !range.value?.[1]) {
    ElMessage.warning('请选择统计区间')
    return
  }
  loading.value = true
  try {
    rows.value = await deliveryApi.performance(range.value[0], range.value[1])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="performance">
    <header class="page-head">
      <h2>我的绩效</h2>
      <span class="sub">按日统计接单、送达与超时情况</span>
    </header>

    <div class="performance__toolbar">
      <el-date-picker
        v-model="range"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :clearable="false"
      />
      <el-button type="primary" :icon="Refresh" :loading="loading" @click="load">查询</el-button>
    </div>

    <div class="performance__metrics">
      <div class="metric-card">
        <span class="metric-card__label">派单总数</span>
        <strong class="metric-card__value">{{ summary.assigned }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-card__label">已接单</span>
        <strong class="metric-card__value">{{ summary.accepted }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-card__label">已送达</span>
        <strong class="metric-card__value">{{ summary.delivered }}</strong>
      </div>
      <div class="metric-card">
        <span class="metric-card__label">超时次数</span>
        <strong class="metric-card__value">{{ summary.timeout }}</strong>
      </div>
    </div>

    <el-card shadow="never" class="performance__panel">
      <template #header>每日明细</template>
      <el-table v-loading="loading" :data="rows" size="small">
        <el-table-column prop="statDate" label="日期" min-width="120" />
        <el-table-column prop="assignedCount" label="派单" width="90" />
        <el-table-column prop="acceptedCount" label="接单" width="90" />
        <el-table-column prop="deliveredCount" label="送达" width="90" />
        <el-table-column prop="timeoutCount" label="超时" width="90" />
        <el-table-column label="准时率" width="110">
          <template #default="{ row }">{{ row.onTimeRate }}%</template>
        </el-table-column>
        <template #empty>该区间暂无配送记录</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.performance {
  @include page-shell;

  &__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    padding: 16px 18px;
    margin-bottom: 18px;
    @include panel;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
    margin-bottom: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
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

@media (max-width: 960px) {
  .performance__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
