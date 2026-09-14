<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'
import { MERCHANT_STATUS, labelOf, tagTypeOf } from '../../constants/dictionaries'
import type { MerchantApplication } from '../../api/merchant'

const applications = ref<MerchantApplication[]>([])
const loading = ref(false)
const reviewing = ref(false)

const dialogVisible = ref(false)
const current = ref<MerchantApplication | null>(null)
const approved = ref(true)
const reviewNote = ref('')

async function load() {
  loading.value = true
  try {
    applications.value = await adminApi.merchantApplications()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openReview(row: MerchantApplication, pass: boolean) {
  current.value = row
  approved.value = pass
  reviewNote.value = ''
  dialogVisible.value = true
}

async function confirmReview() {
  if (!current.value?.merchantId) {
    ElMessage.warning('该申请缺少商家标识，无法审核')
    return
  }
  if (!approved.value && !reviewNote.value.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  reviewing.value = true
  try {
    await adminApi.reviewMerchant(current.value.merchantId, {
      approved: approved.value,
      reviewNote: reviewNote.value.trim() || undefined
    })
    ElMessage.success(approved.value ? '已通过' : '已驳回')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    reviewing.value = false
  }
}



onMounted(load)
</script>

<template>
  <section class="merchants">
    <header class="page-head">
      <h2>商家审核</h2>
      <span class="sub">通过后商家方可上架经营；驳回必须填写原因</span>
    </header>

    <el-card shadow="never" class="merchants__panel">
      <template #header>
        <div class="merchants__header">
          <span>共 {{ applications.length }} 条申请</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="applications" size="small">
        <el-table-column prop="applicationId" label="申请" width="90" />
        <el-table-column prop="merchantId" label="商家" width="90" />
        <el-table-column prop="merchantName" label="店铺名称" min-width="150" />
        <el-table-column prop="applicantUserId" label="申请人" width="100" />
        <el-table-column label="营业执照" min-width="150">
          <template #default="{ row }">
            <el-link v-if="row.businessLicenseUrl" :href="row.businessLicenseUrl" target="_blank" type="primary">
              查看
            </el-link>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="tagTypeOf(MERCHANT_STATUS, row.status)">
              {{ labelOf(MERCHANT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openReview(row, true)">通过</el-button>
            <el-button text type="danger" size="small" @click="openReview(row, false)">驳回</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无商家申请</template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="approved ? '通过入驻申请' : '驳回入驻申请'" width="480px">
      <el-alert
        v-if="current"
        type="info"
        :closable="false"
        :title="`${current.merchantName}（商家 #${current.merchantId ?? '—'}）`"
        class="merchants__dialog-tip"
      />
      <el-form label-position="top">
        <el-form-item :label="approved ? '审核备注（可选）' : '驳回原因（必填）'" :required="!approved">
          <el-input v-model="reviewNote" type="textarea" :rows="3" placeholder="请填写审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :type="approved ? 'primary' : 'danger'" :loading="reviewing" @click="confirmReview">
          {{ approved ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.merchants {
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

  &__dialog-tip {
    margin-bottom: 14px;
  }
}
</style>
