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
</script>

<template>
  <section class="batches">
    <header class="page-head">
      <h2>批次入库</h2>
      <span class="sub">商品、仓库与批次必须属于同一商家；拒绝跨商家、跨仓或未配置分类规则的入库</span>
    </header>

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
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.batches {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__form {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0 16px;
  }
}

@media (max-width: 960px) {
  .batches__form {
    grid-template-columns: 1fr;
  }
}
</style>
