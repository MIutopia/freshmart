<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderApi, type OrderView } from '../../api/trade'
import { errorMessage } from '../../api/http'

const orders = ref<OrderView[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    // 同一接口，后端按当前商家过滤
    orders.value = await orderApi.listMine()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="merchant-orders">
    <header class="page-head">
      <h2>订单处理</h2>
      <span class="sub">仅显示本商家的子订单；平台承担指市场价上浮差额</span>
    </header>

    <el-card shadow="never" class="merchant-orders__panel">
      <template #header>
        <div class="merchant-orders__header">
          <span>共 {{ orders.length }} 条</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="orders" size="small">
        <el-table-column prop="orderNo" label="子订单号" min-width="150" />
        <el-table-column label="仓库" width="80">
          <template #default="{ row }">#{{ row.warehouseId }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="warning">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应付" width="100">
          <template #default="{ row }">¥{{ row.payableAmount }}</template>
        </el-table-column>
        <el-table-column label="实际金额" width="110">
          <template #default="{ row }">{{ row.actualGoodsAmount === null ? '—' : `¥${row.actualGoodsAmount}` }}</template>
        </el-table-column>
        <el-table-column label="平台承担" width="110">
          <template #default="{ row }">
            {{ row.platformAbsorbedAmount === null ? '—' : `¥${row.platformAbsorbedAmount}` }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <template #empty>暂无订单</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.merchant-orders {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    color: $text-secondary;
  }
}
</style>
