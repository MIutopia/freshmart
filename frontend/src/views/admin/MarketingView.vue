<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi, type BatchPromotionCandidate } from '../../api/admin'
import { errorMessage } from '../../api/http'

const candidates = ref<BatchPromotionCandidate[]>([])
const loading = ref(false)
const submitting = ref(false)

const dialogVisible = ref(false)
const current = ref<BatchPromotionCandidate | null>(null)
const markdownRate = ref('0.8')
const startsAt = ref('')
const endsAt = ref('')

async function load() {
  loading.value = true
  try {
    candidates.value = await adminApi.batchPromotionCandidates()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openSchedule(row: BatchPromotionCandidate) {
  current.value = row
  markdownRate.value = '0.8'
  startsAt.value = ''
  endsAt.value = ''
  dialogVisible.value = true
}

/** 后端要求 startsAt/endsAt 为 LocalDateTime，且结束时间不得跨过期日 */
async function confirmSchedule() {
  if (!current.value) return
  if (!markdownRate.value || !startsAt.value || !endsAt.value) {
    ElMessage.warning('折扣率与起止时间必填')
    return
  }
  submitting.value = true
  try {
    await adminApi.createBatchPromotion({
      batchId: current.value.batchId,
      markdownRate: Number(markdownRate.value),
      startsAt: startsAt.value,
      endsAt: endsAt.value
    })
    ElMessage.success('批次促销已排期')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="batch-promotion">
    <header class="page-head">
      <h2>批次促销排期</h2>
      <span class="sub">临期 3 天批次进入候选；促销不得跨过期日，且时间区间不可重叠</span>
    </header>

    <el-card shadow="never" class="batch-promotion__panel">
      <template #header>
        <div class="batch-promotion__header">
          <span>共 {{ candidates.length }} 个候选批次</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新候选</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="candidates" size="small">
        <el-table-column prop="batchId" label="批次" width="80" />
        <el-table-column prop="batchNo" label="批次号" min-width="150" />
        <el-table-column prop="productName" label="商品" min-width="140" />
        <el-table-column prop="warehouseId" label="仓库" width="90" />
        <el-table-column label="可售/预占(g)" width="130">
          <template #default="{ row }">{{ row.availableGrams }} / {{ row.reservedGrams }}</template>
        </el-table-column>
        <el-table-column prop="expiresOn" label="过期日" width="120" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openSchedule(row)">确认排期</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无临期候选批次</template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="确认批次促销排期" width="520px">
      <el-alert
        v-if="current"
        type="info"
        :closable="false"
        :title="`批次 ${current.batchNo} · ${current.productName} · 过期日 ${current.expiresOn}`"
        class="batch-promotion__tip"
      />
      <el-form label-position="top">
        <el-form-item label="折扣率（0.01 ~ 99.99）" required>
          <el-input v-model="markdownRate" placeholder="0.8 表示八折" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmSchedule">确认排期</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.batch-promotion {
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

  &__tip {
    margin-bottom: 14px;
  }
}
</style>
