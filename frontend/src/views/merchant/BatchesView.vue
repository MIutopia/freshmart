<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { catalogApi } from '../../api/catalog'
import { errorMessage } from '../../api/http'

const submitting = ref(false)
const form = ref({
  productId: '',
  warehouseId: '',
  batchNo: '',
  availableGrams: '',
  expiresOn: ''
})

const receipting = ref(false)
const receipt = ref({
  productId: '',
  warehouseId: '',
  batchNo: '',
  receivedGrams: '',
  grossGrams: '',
  tareGrams: '',
  note: ''
})
const lastReceipt = ref<{ receiptNo: string; receivedGrams: number; batchAvailableGrams: number } | null>(null)

/** 净重必须为正；填了毛重时不得小于净重，与后端校验保持一致 */
function receiptValidationError(): string | null {
  const { productId, warehouseId, batchNo, receivedGrams, grossGrams } = receipt.value
  if (!productId || !warehouseId || !batchNo || !receivedGrams) {
    return '商品、仓库、批次号与净重克数均为必填'
  }
  const net = Number(receivedGrams)
  if (!Number.isFinite(net) || net <= 0) {
    return '净重克数必须为正整数'
  }
  if (grossGrams && Number(grossGrams) < net) {
    return '毛重不能小于净重'
  }
  return null
}

async function submit() {
  if (!form.value.productId || !form.value.warehouseId || !form.value.batchNo || !form.value.availableGrams) {
    ElMessage.warning('商品、仓库、批次号与入库克数均为必填')
    return
  }
  submitting.value = true
  try {
    await catalogApi.createBatch({
      productId: Number(form.value.productId),
      warehouseId: Number(form.value.warehouseId),
      batchNo: form.value.batchNo.trim(),
      availableGrams: Number(form.value.availableGrams),
      expiresOn: form.value.expiresOn || undefined
    })
    ElMessage.success('批次入库成功；临期 3 天将进入批次促销候选')
    form.value = { productId: '', warehouseId: '', batchNo: '', availableGrams: '', expiresOn: '' }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function receiveStock() {
  const invalid = receiptValidationError()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }
  receipting.value = true
  try {
    const result = await catalogApi.receiveStock({
      productId: Number(receipt.value.productId),
      warehouseId: Number(receipt.value.warehouseId),
      batchNo: receipt.value.batchNo.trim(),
      receivedGrams: Number(receipt.value.receivedGrams),
      grossGrams: receipt.value.grossGrams ? Number(receipt.value.grossGrams) : undefined,
      tareGrams: receipt.value.tareGrams ? Number(receipt.value.tareGrams) : undefined,
      note: receipt.value.note.trim() || undefined
    })
    lastReceipt.value = {
      receiptNo: result.receiptNo,
      receivedGrams: result.receivedGrams,
      batchAvailableGrams: result.batchAvailableGrams
    }
    ElMessage.success('称收入库成功')
    receipt.value = {
      productId: receipt.value.productId,
      warehouseId: receipt.value.warehouseId,
      batchNo: receipt.value.batchNo,
      receivedGrams: '',
      grossGrams: '',
      tareGrams: '',
      note: ''
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    receipting.value = false
  }
}
</script>

<template>
  <section class="batches">
    <header class="page-head">
      <h2>批次入库与称重</h2>
      <span class="sub">
        商品、仓库与批次必须属于同一商家；拒绝跨商家、跨仓或未配置分类规则的入库；实际到货按称重净重登记
      </span>
    </header>

    <div class="batches__grid">
      <el-card shadow="never" class="batches__panel">
        <template #header>新建批次</template>
        <el-form label-position="top" class="batches__form">
          <el-form-item label="商品 ID" required>
            <el-input v-model="form.productId" placeholder="productId" />
          </el-form-item>
          <el-form-item label="仓库 ID" required>
            <el-input v-model="form.warehouseId" placeholder="warehouseId" />
          </el-form-item>
          <el-form-item label="批次号" required>
            <el-input v-model="form.batchNo" placeholder="如 B20260913-01" />
          </el-form-item>
          <el-form-item label="入库克数" required>
            <el-input v-model="form.availableGrams" placeholder="5000" />
          </el-form-item>
          <el-form-item label="过期日期">
            <el-date-picker v-model="form.expiresOn" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="submitting" @click="submit">入库</el-button>
      </el-card>

      <el-card shadow="never" class="batches__panel">
        <template #header>称收入库</template>
        <el-form label-position="top" class="batches__form">
          <el-form-item label="商品 ID" required>
            <el-input v-model="receipt.productId" placeholder="productId" />
          </el-form-item>
          <el-form-item label="仓库 ID" required>
            <el-input v-model="receipt.warehouseId" placeholder="warehouseId" />
          </el-form-item>
          <el-form-item label="批次号" required>
            <el-input v-model="receipt.batchNo" placeholder="已有批次号则累加库存" />
          </el-form-item>
          <el-form-item label="净重克数" required>
            <el-input v-model="receipt.receivedGrams" placeholder="实际入库净重" />
          </el-form-item>
          <el-form-item label="毛重克数">
            <el-input v-model="receipt.grossGrams" placeholder="选填，仅留痕" />
          </el-form-item>
          <el-form-item label="皮重克数">
            <el-input v-model="receipt.tareGrams" placeholder="选填，仅留痕" />
          </el-form-item>
        </el-form>
        <el-form-item label="备注">
          <el-input v-model="receipt.note" placeholder="如 供应商 / 车次" />
        </el-form-item>
        <el-button type="primary" :loading="receipting" @click="receiveStock">称收入库</el-button>
        <el-alert
          v-if="lastReceipt"
          class="batches__result"
          type="success"
          :closable="false"
          show-icon
          :title="'入库单 ' + lastReceipt.receiptNo"
          :description="'本次入库 ' + lastReceipt.receivedGrams + ' 克，该批次当前可用 ' + lastReceipt.batchAvailableGrams + ' 克'"
        />
        <p class="batches__hint">
          净重才是库存增量，毛重与皮重仅用于追溯账实差异；重复提交同一请求不会重复入库。
        </p>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.batches {
  @include page-shell;

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    align-items: start;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__form {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0 16px;
  }

  &__result {
    margin-top: 14px;
  }

  &__hint {
    margin: 14px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: $text-muted;
  }
}

@media (max-width: 960px) {
  .batches__grid,
  .batches__form {
    grid-template-columns: 1fr;
  }
}
</style>
