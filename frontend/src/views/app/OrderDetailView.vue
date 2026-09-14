<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderApi, type OrderView } from '../../api/trade'
import { errorMessage } from '../../api/http'
import { ORDER_STATUS, labelOf, tagTypeOf } from '../../constants/dictionaries'

const route = useRoute()
const orderId = Number(route.params.orderId)

const order = ref<OrderView | null>(null)
const loading = ref(false)
const receiptVisible = ref(false)
const traceVisible = ref(false)
const receipt = ref<Record<string, unknown> | null>(null)
const trace = ref<Record<string, unknown>[]>([])

async function load() {
  loading.value = true
  try {
    order.value = await orderApi.detail(orderId)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function openReceipt() {
  try {
    receipt.value = await orderApi.receipt(orderId)
    receiptVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function openTrace() {
  try {
    trace.value = await orderApi.traceability(orderId)
    traceVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

onMounted(load)
</script>

<template>
  <section class="order-detail">
    <header class="page-head">
      <h2>订单详情</h2>
      <span class="sub">子订单 #{{ orderId }}</span>
    </header>

    <el-skeleton v-if="loading" :rows="5" animated class="order-detail__skeleton" />

    <el-card v-else-if="order" shadow="never" class="order-detail__panel">
      <template #header>
        <div class="order-detail__header">
          <span class="order-detail__no">{{ order.orderNo }}</span>
          <el-tag size="small" effect="light" :type="tagTypeOf(ORDER_STATUS, order.status)">
            {{ labelOf(ORDER_STATUS, order.status) }}
          </el-tag>
        </div>
      </template>

      <div class="order-detail__stats">
        <div class="order-detail__stat">
          <span class="order-detail__stat-label">应付金额</span>
          <strong>¥{{ order.payableAmount }}</strong>
        </div>
        <div class="order-detail__stat">
          <span class="order-detail__stat-label">实际商品金额</span>
          <strong>{{ order.actualGoodsAmount === null ? '—' : `¥${order.actualGoodsAmount}` }}</strong>
        </div>
        <div class="order-detail__stat">
          <span class="order-detail__stat-label">平台承担</span>
          <strong>{{ order.platformAbsorbedAmount === null ? '—' : `¥${order.platformAbsorbedAmount}` }}</strong>
        </div>
      </div>

      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="商家 / 仓库">#{{ order.merchantId }} / #{{ order.warehouseId }}</el-descriptions-item>
        <el-descriptions-item label="配送区域">#{{ order.deliveryZoneId }}</el-descriptions-item>
        <el-descriptions-item label="商品金额">¥{{ order.goodsAmount }}</el-descriptions-item>
        <el-descriptions-item label="配送费">¥{{ order.freightAmount }}</el-descriptions-item>
        <el-descriptions-item label="优惠金额">¥{{ order.discountAmount }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ order.createdAt }}</el-descriptions-item>
      </el-descriptions>

      <div class="order-detail__actions">
        <el-button @click="openReceipt">电子小票</el-button>
        <el-button @click="openTrace">批次追溯</el-button>
        <router-link to="/app/refunds"><el-button type="primary">申请售后</el-button></router-link>
      </div>
    </el-card>

    <el-dialog v-model="receiptVisible" title="电子小票" width="640px">
      <pre class="order-detail__json">{{ JSON.stringify(receipt, null, 2) }}</pre>
    </el-dialog>

    <el-dialog v-model="traceVisible" title="批次追溯" width="640px">
      <pre class="order-detail__json">{{ JSON.stringify(trace, null, 2) }}</pre>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.order-detail {
  @include page-shell;

  &__skeleton {
    padding: 20px;
    @include panel;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__no {
    font-family: Consolas, monospace;
    font-size: 14px;
    color: $text-primary;
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
    margin-bottom: 18px;
  }

  &__stat {
    padding: 14px 16px;
    background: $canvas;
    border: 1px solid $border;
    border-radius: $radius-md;

    strong {
      display: block;
      margin-top: 6px;
      font-size: 19px;
      color: $text-primary;
    }
  }

  &__stat-label {
    font-size: 12px;
    color: $text-muted;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 18px;
  }

  &__json {
    margin: 0;
    max-height: 420px;
    overflow: auto;
    font-family: Consolas, monospace;
    font-size: 12px;
    line-height: 1.7;
    color: $text-secondary;
  }
}

@media (max-width: 960px) {
  .order-detail__stats {
    grid-template-columns: 1fr;
  }
}
</style>
