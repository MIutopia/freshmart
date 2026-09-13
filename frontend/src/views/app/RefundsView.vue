<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mediaApi, refundApi, type RefundView } from '../../api/afterSale'
import { errorMessage } from '../../api/http'

/** 后端 RefundRequest 只接受 orderId / issueType / description / evidenceImages */
const form = ref({ orderId: '', issueType: 'OUT_OF_STOCK', description: '' })
const evidenceImages = ref<string[]>([])

const refunds = ref<RefundView[]>([])
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)

const ISSUE_OPTIONS = [
  { label: '缺货', value: 'OUT_OF_STOCK' },
  { label: '品质问题', value: 'QUALITY' },
  { label: '其他', value: 'OTHER' }
]

async function loadRefunds() {
  loading.value = true
  try {
    refunds.value = await refundApi.listMine()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function uploadEvidence(options: { file: File }) {
  uploading.value = true
  try {
    const asset = await mediaApi.upload(options.file)
    evidenceImages.value.push(asset.url)
    ElMessage.success('凭证已上传')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    uploading.value = false
  }
}

async function submit() {
  if (!form.value.orderId || !form.value.description.trim()) {
    ElMessage.warning('订单 ID 与问题描述必填')
    return
  }
  if (evidenceImages.value.length === 0) {
    ElMessage.warning('售后必须上传至少一张凭证图片')
    return
  }
  submitting.value = true
  try {
    await refundApi.create({
      orderId: Number(form.value.orderId),
      issueType: form.value.issueType,
      description: form.value.description.trim(),
      evidenceImages: evidenceImages.value
    })
    ElMessage.success('售后申请已提交，等待客服审核')
    form.value = { orderId: '', issueType: 'OUT_OF_STOCK', description: '' }
    evidenceImages.value = []
    await loadRefunds()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

function statusTagType(status: string) {
  if (['REFUND_SUCCESS', 'MANUAL_REFUND_COMPLETED'].includes(status)) return 'success'
  if (['REFUND_FAIL', 'REJECTED'].includes(status)) return 'danger'
  return 'warning'
}

onMounted(loadRefunds)
</script>

<template>
  <section class="refunds">
    <header class="page-head">
      <h2>售后退款</h2>
      <span class="sub">仅支持缺货与品质问题，需在送达后 1 天内提交图片与描述</span>
    </header>

    <div class="refunds__grid">
      <el-card shadow="never" class="refunds__panel">
        <template #header>提交售后申请</template>
        <el-form label-position="top">
          <el-form-item label="订单 ID" required>
            <el-input v-model="form.orderId" placeholder="从订单详情页获取" />
          </el-form-item>
          <el-form-item label="问题类型" required>
            <el-select v-model="form.issueType">
              <el-option v-for="item in ISSUE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="问题描述" required>
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请描述商品问题" />
          </el-form-item>
          <el-form-item label="举证图片" required>
            <el-upload
              :show-file-list="false"
              accept="image/*"
              :http-request="uploadEvidence as any"
              :disabled="uploading"
            >
              <el-button :loading="uploading">上传图片</el-button>
            </el-upload>
            <div v-if="evidenceImages.length" class="refunds__thumbs">
              <el-image
                v-for="url in evidenceImages"
                :key="url"
                :src="url"
                fit="cover"
                class="refunds__thumb"
                :preview-src-list="evidenceImages"
              />
            </div>
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="submitting" class="refunds__submit" @click="submit">提交申请</el-button>
      </el-card>

      <el-card shadow="never" class="refunds__panel">
        <template #header>我的售后</template>
        <el-table v-loading="loading" :data="refunds" size="small">
          <el-table-column prop="refundNo" label="退款单号" min-width="130" />
          <el-table-column prop="orderId" label="订单" width="80" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              {{ ISSUE_OPTIONS.find((item) => item.value === row.issueType)?.label ?? row.issueType }}
            </template>
          </el-table-column>
          <el-table-column label="金额" width="90">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="light">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <template #empty>暂无售后记录</template>
        </el-table>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.refunds {
  @include page-shell;

  &__grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
    gap: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__thumbs {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }

  &__thumb {
    width: 72px;
    height: 72px;
    border: 1px solid $border;
    border-radius: $radius-sm;
  }

  &__submit {
    width: 100%;
  }
}

@media (max-width: 960px) {
  .refunds__grid {
    grid-template-columns: 1fr;
  }
}
</style>
