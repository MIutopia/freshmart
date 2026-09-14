<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderApi, type OrderView } from '../../api/trade'
import { weighingApi, type WeighingSheet } from '../../api/weighing'
import { errorMessage } from '../../api/http'
import { ORDER_STATUS, WEIGHING_ACTION, labelOf, tagTypeOf } from '../../constants/dictionaries'

const orders = ref<OrderView[]>([])
const loading = ref(false)

const weighVisible = ref(false)
const weighing = ref(false)
const sheetLoading = ref(false)
const weighedOrder = ref<OrderView | null>(null)
const sheet = ref<WeighingSheet | null>(null)
/** 以 orderItemId 为键，避免表格重排后录入值与订单项错位 */
const itemInputs = ref<Record<number, { actualGrams: string; actualGoodsAmount: string }>>({})
const weighNote = ref('')

const totals = computed(() => {
  if (!sheet.value) return { grams: 0, amount: 0 }
  let grams = 0
  let amount = 0
  for (const item of sheet.value.items) {
    const input = itemInputs.value[item.orderItemId]
    const itemGrams = Number(input?.actualGrams ?? '')
    const itemAmount = Number(input?.actualGoodsAmount ?? '')
    if (Number.isFinite(itemGrams)) grams += itemGrams
    if (Number.isFinite(itemAmount)) amount += itemAmount
  }
  return { grams, amount: Math.round(amount * 100) / 100 }
})

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

/** 只有已支付并进入分拣之后的订单才能称重，与后端状态校验一致 */
function canWeigh(row: OrderView) {
  return ['WAITING_PICKING', 'PICKED', 'DELIVERED'].includes(row.status)
}

async function openWeigh(row: OrderView) {
  weighedOrder.value = row
  sheet.value = null
  itemInputs.value = {}
  weighNote.value = ''
  weighVisible.value = true
  sheetLoading.value = true
  try {
    const result = await weighingApi.sheet(row.id)
    sheet.value = result
    const inputs: Record<number, { actualGrams: string; actualGoodsAmount: string }> = {}
    for (const item of result.items) {
      inputs[item.orderItemId] = {
        actualGrams: item.actualGrams === null ? '' : String(item.actualGrams),
        actualGoodsAmount: item.actualGoodsAmount === null ? '' : String(item.actualGoodsAmount)
      }
    }
    itemInputs.value = inputs
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    sheetLoading.value = false
  }
}

async function submitWeigh() {
  const order = weighedOrder.value
  const current = sheet.value
  if (!order || !current) return
  if (current.items.length === 0) {
    ElMessage.warning('该订单没有可称重的订单项')
    return
  }
  const items = []
  for (const item of current.items) {
    const input = itemInputs.value[item.orderItemId]
    const grams = Number(input?.actualGrams ?? '')
    const amount = Number(input?.actualGoodsAmount ?? '')
    if (!Number.isFinite(grams) || grams <= 0) {
      ElMessage.warning(`${item.productName} 的实际克数必须为正整数`)
      return
    }
    if (!Number.isFinite(amount) || amount < 0) {
      ElMessage.warning(`${item.productName} 的实际金额不能为负`)
      return
    }
    items.push({ orderItemId: item.orderItemId, actualGrams: grams, actualGoodsAmount: amount })
  }
  weighing.value = true
  try {
    const result = await weighingApi.submit({
      orderId: order.id,
      items,
      note: weighNote.value.trim() || undefined
    })
    const gramsHint = result.inventoryAdjustGrams === 0
      ? '未调整库存'
      : `库存差额 ${result.inventoryAdjustGrams} 克`
    ElMessage.success(`称重已提交：${labelOf(WEIGHING_ACTION, result.action)}，${gramsHint}`)
    weighVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    weighing.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="merchant-orders">
    <header class="page-head">
      <h2>订单处理</h2>
      <span class="sub">
        仅显示本商家的子订单；称重按订单项录入实际净重与金额，克数差额会逐项回补批次库存
      </span>
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
            <el-tag size="small" effect="light" :type="tagTypeOf(ORDER_STATUS, row.status)">
              {{ labelOf(ORDER_STATUS, row.status) }}
            </el-tag>
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
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :disabled="!canWeigh(row)" @click="openWeigh(row)">
              逐项称重
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无订单</template>
      </el-table>
    </el-card>

    <el-dialog v-model="weighVisible" title="逐项称重" width="720px">
      <el-form label-position="top">
        <el-form-item label="子订单">
          <el-input :model-value="weighedOrder?.orderNo ?? ''" disabled />
        </el-form-item>
      </el-form>

      <el-table v-loading="sheetLoading" :data="sheet?.items ?? []" size="small" border>
        <el-table-column prop="productName" label="商品" min-width="140" />
        <el-table-column label="预估克数" width="100">
          <template #default="{ row }">{{ row.prepaidGrams }}</template>
        </el-table-column>
        <el-table-column label="预估金额" width="100">
          <template #default="{ row }">¥{{ row.prepaidGoodsAmount }}</template>
        </el-table-column>
        <el-table-column label="实际克数" width="130">
          <template #default="{ row }">
            <el-input v-model="itemInputs[row.orderItemId].actualGrams" size="small" placeholder="净重" />
          </template>
        </el-table-column>
        <el-table-column label="实际金额" width="130">
          <template #default="{ row }">
            <el-input v-model="itemInputs[row.orderItemId].actualGoodsAmount" size="small" placeholder="金额" />
          </template>
        </el-table-column>
        <template #empty>该订单没有可称重的订单项</template>
      </el-table>

      <div class="merchant-orders__totals">
        <span>合计：{{ totals.grams }} 克 / ¥{{ totals.amount }}</span>
        <span class="merchant-orders__hint">预估：¥{{ sheet?.prepaidGoodsAmount ?? '—' }}</span>
      </div>

      <el-form label-position="top">
        <el-form-item label="备注">
          <el-input v-model="weighNote" placeholder="选填" />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="金额差额在平台误差上限内由平台承担，低于预估退回用户；超过上限转人工复核。"
      />
      <template #footer>
        <el-button @click="weighVisible = false">取消</el-button>
        <el-button type="primary" :loading="weighing" :disabled="sheetLoading || !sheet" @click="submitWeigh">
          提交
        </el-button>
      </template>
    </el-dialog>
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

  &__totals {
    display: flex;
    align-items: center;
    gap: 16px;
    margin: 14px 0;
    font-size: 13px;
    color: $text-secondary;
  }

  &__hint {
    color: $text-muted;
  }
}
</style>
