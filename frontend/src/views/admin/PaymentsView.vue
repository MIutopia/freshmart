<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'
import { RECONCILIATION_STATUS, labelOf, optionsOf, tagTypeOf } from '../../constants/dictionaries'

const RECONCILIATION_OPTIONS = optionsOf(RECONCILIATION_STATUS)
import type { ReconciliationDifferenceView } from '../../api/trade'

const differences = ref<ReconciliationDifferenceView[]>([])
const status = ref('UNHANDLED')
const loading = ref(false)
const busy = ref(false)

const dialogVisible = ref(false)
const action = ref<'claim' | 'shelve' | 'resolve'>('claim')
const current = ref<ReconciliationDifferenceView | null>(null)
const note = ref('')
const resolution = ref('PAID')

const RESOLUTIONS = [
  { label: '已收款 PAID', value: 'PAID' },
  { label: '待确认 PENDING', value: 'PENDING' },
  { label: '无关流水 IRRELEVANT', value: 'IRRELEVANT' }
]

async function load() {
  loading.value = true
  try {
    differences.value = await adminApi.reconciliationDifferences(status.value || undefined)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openAction(row: ReconciliationDifferenceView, kind: 'claim' | 'shelve' | 'resolve') {
  current.value = row
  action.value = kind
  note.value = ''
  resolution.value = 'PAID'
  dialogVisible.value = true
}

/** 三个接口的 note 均为 @NotBlank 必填；resolve 还需 resolution */
async function confirmAction() {
  if (!current.value) return
  if (!note.value.trim()) {
    ElMessage.warning('请填写说明，后端要求必填')
    return
  }
  busy.value = true
  try {
    const id = current.value.id
    const payload = { note: note.value.trim() }
    if (action.value === 'claim') await adminApi.claimDifference(id, payload)
    else if (action.value === 'shelve') await adminApi.shelveDifference(id, payload)
    else await adminApi.resolveDifference(id, { resolution: resolution.value, note: payload.note })

    ElMessage.success('操作成功')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busy.value = false
  }
}



const ACTION_TITLE: Record<'claim' | 'shelve' | 'resolve', string> = {
  claim: '认领对账差异',
  shelve: '暂存对账差异',
  resolve: '处理对账差异'
}

onMounted(load)
</script>

<template>
  <section class="payments">
    <header class="page-head">
      <h2>支付核验与对账</h2>
      <span class="sub">对账差异按「未处理 → 处理中 → 已处理」流转，也可先暂存待后续跟进</span>
    </header>

    <div class="payments__toolbar">
      <el-select v-model="status" class="payments__filter" @change="load">
        <el-option label="全部" value="" />
        <el-option
          v-for="item in RECONCILIATION_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="payments__panel">
      <el-table v-loading="loading" :data="differences" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="paymentNo" label="支付单" min-width="150" />
        <el-table-column prop="differenceType" label="差异类型" min-width="150" />
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="tagTypeOf(RECONCILIATION_STATUS, row.status)">
              {{ labelOf(RECONCILIATION_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="认领人" width="90">
          <template #default="{ row }">{{ row.claimedBy ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openAction(row, 'claim')">认领</el-button>
            <el-button text size="small" @click="openAction(row, 'shelve')">暂存</el-button>
            <el-button text type="primary" size="small" @click="openAction(row, 'resolve')">处理</el-button>
          </template>
        </el-table-column>
        <template #empty>当前筛选下没有对账差异</template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="ACTION_TITLE[action]" width="480px">
      <el-alert
        v-if="current"
        type="info"
        :closable="false"
        :title="`差异 #${current.id} · ${current.differenceType}`"
        class="payments__dialog-tip"
      />
      <el-form label-position="top">
        <el-form-item v-if="action === 'resolve'" label="处理结论" required>
          <el-select v-model="resolution">
            <el-option v-for="item in RESOLUTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" required>
          <el-input v-model="note" type="textarea" :rows="3" placeholder="请填写说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="confirmAction">确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.payments {
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
    width: 220px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__dialog-tip {
    margin-bottom: 14px;
  }
}
</style>
