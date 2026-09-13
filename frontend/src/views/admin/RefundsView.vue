<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'
import type { RefundSuggestionView, RefundView } from '../../api/afterSale'

const refunds = ref<RefundView[]>([])
const status = ref('')
const loading = ref(false)
const busy = ref(false)

const reviewVisible = ref(false)
const current = ref<RefundView | null>(null)
const approved = ref(true)
const reviewNote = ref('')

const suggestionVisible = ref(false)
const suggestion = ref<RefundSuggestionView | null>(null)

async function load() {
  loading.value = true
  try {
    refunds.value = await adminApi.refunds(status.value || undefined)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openReview(row: RefundView, pass: boolean) {
  current.value = row
  approved.value = pass
  reviewNote.value = ''
  reviewVisible.value = true
}

async function confirmReview() {
  if (!current.value) return
  if (!reviewNote.value.trim()) {
    ElMessage.warning('审核意见必填')
    return
  }
  busy.value = true
  try {
    await adminApi.reviewRefund(current.value.id, { approved: approved.value, reviewNote: reviewNote.value.trim() })
    ElMessage.success(approved.value ? '审核已通过' : '已驳回')
    reviewVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busy.value = false
  }
}

async function completeRefund(row: RefundView) {
  try {
    await ElMessageBox.confirm(`确认已完成 ${row.refundNo} 的微信转账？`, '确认人工退款完成', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminApi.completeRefund(row.refundNo)
    ElMessage.success('已确认退款完成，订单、佣金、积分与库存处置将联动')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

/** manual-fail / manual-retry 的 reason 均为必填 */
async function askReason(row: RefundView, kind: 'fail' | 'retry') {
  let reason: string
  try {
    const result = await ElMessageBox.prompt(kind === 'fail' ? '失败原因' : '重新发起原因', '请填写原因', {
      inputPlaceholder: '必填',
      inputValidator: (value: string) => (value && value.trim() ? true : '原因不能为空')
    })
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    if (kind === 'fail') await adminApi.failRefund(row.refundNo, { reason })
    else await adminApi.retryRefund(row.refundNo, { reason })
    ElMessage.success(kind === 'fail' ? '已标记退款失败' : '已重新进入待人工退款')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function loadSuggestion(row: RefundView) {
  try {
    suggestion.value = await adminApi.refundAiSuggestion(row.refundNo)
    suggestionVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function statusTagType(value: string) {
  if (['REFUND_SUCCESS', 'MANUAL_REFUND_COMPLETED'].includes(value)) return 'success'
  if (['REFUND_FAIL', 'REJECTED'].includes(value)) return 'danger'
  return 'warning'
}

onMounted(load)
</script>

<template>
  <section class="admin-refunds">
    <header class="page-head">
      <h2>售后审核与人工退款</h2>
      <span class="sub">个人收款码订单审核通过后进入人工退款工单，确认转账后才联动订单状态</span>
    </header>

    <div class="admin-refunds__toolbar">
      <el-input v-model="status" class="admin-refunds__filter" placeholder="按状态筛选，留空为全部" clearable />
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="admin-refunds__panel">
      <el-table v-loading="loading" :data="refunds" size="small">
        <el-table-column prop="refundNo" label="退款单号" min-width="150" />
        <el-table-column prop="orderId" label="订单" width="80" />
        <el-table-column prop="issueType" label="类型" width="120" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="180">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openReview(row, true)">通过</el-button>
            <el-button text type="danger" size="small" @click="openReview(row, false)">驳回</el-button>
            <el-button text size="small" @click="completeRefund(row)">退款完成</el-button>
            <el-button text type="danger" size="small" @click="askReason(row, 'fail')">失败</el-button>
            <el-button text size="small" @click="askReason(row, 'retry')">重新发起</el-button>
            <el-button text type="primary" size="small" @click="loadSuggestion(row)">AI 建议</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无退款工单</template>
      </el-table>
    </el-card>

    <el-dialog v-model="reviewVisible" :title="approved ? '通过售后申请' : '驳回售后申请'" width="480px">
      <el-alert
        v-if="current"
        type="info"
        :closable="false"
        :title="`${current.refundNo} · ¥${current.amount} · ${current.issueType}`"
        class="admin-refunds__tip"
      />
      <el-form label-position="top">
        <el-form-item label="审核意见" required>
          <el-input v-model="reviewNote" type="textarea" :rows="3" placeholder="请填写审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button :type="approved ? 'primary' : 'danger'" :loading="busy" @click="confirmReview">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="suggestionVisible" title="AI 辅助审核建议" width="520px">
      <template v-if="suggestion">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="退款单">{{ suggestion.refundNo }}</el-descriptions-item>
          <el-descriptions-item label="问题类型">{{ suggestion.issueType }}</el-descriptions-item>
          <el-descriptions-item label="凭证数量">{{ suggestion.evidenceCount }}</el-descriptions-item>
          <el-descriptions-item label="建议">{{ suggestion.suggestion }}</el-descriptions-item>
        </el-descriptions>
        <p class="admin-refunds__disclaimer">{{ suggestion.disclaimer }}</p>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.admin-refunds {
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

  &__filter {
    width: 240px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__tip {
    margin-bottom: 14px;
  }

  &__disclaimer {
    margin: 14px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: $text-muted;
  }
}
</style>
