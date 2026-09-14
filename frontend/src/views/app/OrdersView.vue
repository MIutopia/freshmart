<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderApi, type OrderView } from '../../api/trade'
import { errorMessage } from '../../api/http'
import { ORDER_STATUS, labelOf, tagTypeOf } from '../../constants/dictionaries'

const orders = ref<OrderView[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
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
  <section class="orders">
    <header class="page-head">
      <h2>我的订单</h2>
      <span class="sub">消费者仅可见本人订单；商家与管理员按各自数据边界查看</span>
    </header>

    <el-card shadow="never" class="orders__panel">
      <template #header>
        <div class="orders__header">
          <span>共 {{ orders.length }} 条子订单</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="orders" size="small">
        <el-table-column prop="orderNo" label="子订单号" min-width="150" />
        <el-table-column label="商家" width="80">
          <template #default="{ row }">#{{ row.merchantId }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="tagTypeOf(ORDER_STATUS, row.status)" size="small" effect="light">
              {{ labelOf(ORDER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品金额" width="100">
          <template #default="{ row }">¥{{ row.goodsAmount }}</template>
        </el-table-column>
        <el-table-column label="配送费" width="90">
          <template #default="{ row }">¥{{ row.freightAmount }}</template>
        </el-table-column>
        <el-table-column label="应付" width="100">
          <template #default="{ row }">
            <strong>¥{{ row.payableAmount }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <router-link :to="`/app/orders/${row.id}`">
              <el-button text type="primary" size="small">详情</el-button>
            </router-link>
          </template>
        </el-table-column>
        <template #empty>暂无订单</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.orders {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: $text-secondary;
    font-size: 13px;
  }
}
</style>
