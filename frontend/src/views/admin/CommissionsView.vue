<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'
import type { MerchantSettlement } from '../../api/merchant'

const settlements = ref<MerchantSettlement[]>([])
const loading = ref(false)
const generating = ref(false)
const confirming = ref(false)

const dialogVisible = ref(false)
const current = ref<MerchantSettlement | null>(null)
const note = ref('')

async function load() {
  loading.value = true
  try {
    settlements.value = await adminApi.commissions()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function generate() {
  generating.value = true
  try {
    const created = await adminApi.generateCommissions()
    ElMessage.success(`已生成 ${created.length} 条结算单`)
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    generating.value = false
  }
}

function openConfirm(row: MerchantSettlement) {
  current.value = row
  note.value = ''
  dialogVisible.value = true
}

/** 后端 SettlementConfirmationRequest.note 必填 */
async function confirmSettlement() {
  if (!current.value) return
  if (!note.value.trim()) {
    ElMessage.warning('确认结算必须填写备注')
    return
  }
  confirming.value = true
  try {
    await adminApi.confirmSettlement(current.value.id, { note: note.value.trim() })
    ElMessage.success('结算单已确认')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    confirming.value = false
  }
}

function statusTagType(status: string) {
  if (status === 'SETTLED') return 'success'
  if (status === 'REVERSED') return 'danger'
  return 'warning'
}

onMounted(load)
</script>

<template>
  <section class="commissions">
    <header class="page-head">
      <h2>佣金与结算</h2>
      <span class="sub">佣金基数排除平台市场价补贴；退款会反冲已生成佣金</span>
    </header>

    <el-card shadow="never" class="commissions__panel">
      <template #header>
        <div class="commissions__header">
          <span>共 {{ settlements.length }} 条结算单</span>
          <div class="commissions__actions">
            <el-button type="primary" :loading="generating" @click="generate">生成结算单</el-button>
            <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="settlements" size="small">
        <el-table-column prop="id" label="结算单" width="90" />
        <el-table-column prop="merchantId" label="商家" width="80" />
        <el-table-column prop="orderId" label="子订单" width="90" />
        <el-table-column label="毛收入" width="110">
          <template #default="{ row }">¥{{ row.grossAmount }}</template>
        </el-table-column>
        <el-table-column label="佣金" width="120">
          <template #default="{ row }">¥{{ row.commissionAmount }}（{{ row.commissionRate }}%）</template>
        </el-table-column>
        <el-table-column label="净收入" width="110">
          <template #default="{ row }">¥{{ row.netAmount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结算备注" min-width="140">
          <template #default="{ row }">{{ row.settlementNote || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              size="small"
              :disabled="row.status === 'SETTLED' || row.status === 'REVERSED'"
              @click="openConfirm(row)"
            >
              确认结算
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无结算单</template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="确认结算" width="480px">
      <el-alert
        v-if="current"
        type="info"
        :closable="false"
        :title="`结算单 #${current.id} · 商家 #${current.merchantId} · 净收入 ¥${current.netAmount}`"
        class="commissions__dialog-tip"
      />
      <el-form label-position="top">
        <el-form-item label="结算备注" required>
          <el-input v-model="note" type="textarea" :rows="3" placeholder="请填写本次结算说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="confirmSettlement">确认结算</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.commissions {
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

  &__actions {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__dialog-tip {
    margin-bottom: 14px;
  }
}
</style>
