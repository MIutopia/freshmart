<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderApi, type OrderView } from '../../api/trade'
import { weighingApi } from '../../api/weighing'
import { errorMessage } from '../../api/http'

const orders = ref<OrderView[]>([])
const loading = ref(false)

const weighVisible = ref(false)
const weighing = ref(false)
const weighedOrder = ref<OrderView | null>(null)
const weighForm = ref({ actualGoodsAmount: '', actualGrams: '', note: '' })

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

function openWeigh(row: OrderView) {
  weighedOrder.value = row
  weighForm.value = {
    actualGoodsAmount: row.actualGoodsAmount === null ? '' : String(row.actualGoodsAmount),
    actualGrams: '',
    note: ''
  }
  weighVisible.value = true
}

async function submitWeigh() {
  const order = weighedOrder.value
  if (!order) return
  const amount = Number(weighForm.value.actualGoodsAmount)
  if (!weighForm.value.actualGoodsAmount || !Number.isFinite(amount) || amount < 0) {
    ElMessage.warning('实际商品金额必填且不能为负')
    return
  }
  const grams = weighForm.value.actualGrams ? Number(weighForm.value.actualGrams) : undefined
  if (grams !== undefined && (!Number.isFinite(grams) || grams <= 0)) {
    ElMessage.warning('实际克数必须为正整数')
    return
  }
  weighing.value = true
  try {
    const result = await weighingApi.submit({
      orderId: order.id,
      actualGoodsAmount: amount,
      actualGrams: grams,
      note: weighForm.value.note.trim() || undefined
    })
    const gramsHint = result.inventoryAdjustGrams === 0
      ? '未调整库存'
      : `库存差额 ${result.inventoryAdjustGrams} 克`
    ElMessage.success(`称重已提交：${result.action}，${gramsHint}`)
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
        仅显示本商家的子订单；称重调整需填写实际净重克数，未填写时只结算金额、不回补库存
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
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :disabled="!canWeigh(row)" @click="openWeigh(row)">
              称重调整
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无订单</template>
      </el-table>
    </el-card>

    <el-dialog v-model="weighVisible" title="称重调整" width="460px">
      <el-form label-position="top">
        <el-form-item label="子订单">
          <el-input :model-value="weighedOrder?.orderNo ?? ''" disabled />
        </el-form-item>
        <el-form-item label="预估商品金额">
          <el-input :model-value="weighedOrder ? `¥${weighedOrder.payableAmount}` : ''" disabled />
        </el-form-item>
        <el-form-item label="实际商品金额" required>
          <el-input v-model="weighForm.actualGoodsAmount" placeholder="拣货称重后的实际金额" />
        </el-form-item>
        <el-form-item label="实际净重克数">
          <el-input v-model="weighForm.actualGrams" placeholder="选填；填写后回补批次库存差额" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="weighForm.note" placeholder="选填" />
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
        <el-button type="primary" :loading="weighing" @click="submitWeigh">提交</el-button>
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
}
</style>
