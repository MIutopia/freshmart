<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { merchantApi, type MerchantDashboard } from '../../api/merchant'
import { errorMessage } from '../../api/http'

/** 后端 /api/merchant/dashboard 的 from/to 为必填日期参数 */
function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date(end.getTime() - 29 * 24 * 60 * 60 * 1000)
  const format = (date: Date) => date.toISOString().slice(0, 10)
  return [format(start), format(end)]
}

const range = ref<[string, string]>(defaultRange())
const data = ref<MerchantDashboard | null>(null)
const loading = ref(false)

const topMetrics = computed(() => {
  if (!data.value) return []
  return [
    { label: '已支付订单', value: data.value.paidOrderCount },
    { label: '销售额', value: `¥${data.value.paidAmount}` },
    { label: '客单价', value: `¥${data.value.averageTicket}` },
    { label: '已退款', value: `¥${data.value.refundedAmount}` },
    { label: '已送达子订单', value: data.value.deliveredOrderCount },
    { label: '配送任务', value: data.value.delivery?.taskCount ?? 0 },
    { label: '待派单', value: data.value.delivery?.waitingAssignmentCount ?? 0 },
    { label: '配送中', value: data.value.delivery?.inProgressCount ?? 0 }
  ]
})

async function load() {
  if (!range.value?.[0] || !range.value?.[1]) {
    ElMessage.warning('请选择统计区间')
    return
  }
  loading.value = true
  try {
    data.value = await merchantApi.dashboard(range.value[0], range.value[1])
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="dashboard">
    <header class="page-head">
      <h2>经营看板</h2>
      <span class="sub">销售额、订单量、客单价、库存预警与履约指标</span>
    </header>

    <div class="dashboard__toolbar">
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

    <el-skeleton v-if="loading && !data" :rows="5" animated class="dashboard__skeleton" />

    <template v-else-if="data">
      <div class="dashboard__metrics">
        <div v-for="metric in topMetrics" :key="metric.label" class="metric-card">
          <span class="metric-card__label">{{ metric.label }}</span>
          <strong class="metric-card__value">{{ metric.value }}</strong>
        </div>
      </div>

      <div class="dashboard__grid">
        <el-card shadow="never" class="dashboard__panel">
          <template #header>库存预警</template>
          <el-table :data="data.lowStock" size="small">
            <el-table-column prop="productName" label="商品" min-width="140" />
            <el-table-column prop="warehouseId" label="仓库" width="90" />
            <el-table-column prop="availableGrams" label="可售(g)" width="100" />
            <template #empty>暂无预警</template>
          </el-table>
        </el-card>

        <el-card shadow="never" class="dashboard__panel">
          <template #header>销量排行</template>
          <el-table :data="data.topProducts" size="small">
            <el-table-column prop="productName" label="商品" min-width="140" />
            <el-table-column prop="soldGrams" label="销量(g)" width="100" />
            <el-table-column label="销售额" width="110">
              <template #default="{ row }">¥{{ row.salesAmount }}</template>
            </el-table-column>
            <template #empty>暂无数据</template>
          </el-table>
        </el-card>
      </div>
    </template>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.dashboard {
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

  &__skeleton {
    padding: 20px;
    @include panel;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
    margin-bottom: 16px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
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

@media (max-width: 1280px) {
  .dashboard__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .dashboard {
    &__metrics {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    &__grid {
      grid-template-columns: 1fr;
    }
  }
}

@media (max-width: 640px) {
  .dashboard__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
