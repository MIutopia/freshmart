<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'
import type { DeliveryTask } from '../../api/delivery'

const tasks = ref<DeliveryTask[]>([])
const status = ref('WAITING_ASSIGNMENT')
const loading = ref(false)
const assigning = ref(false)

const dialogVisible = ref(false)
const current = ref<DeliveryTask | null>(null)
const riderUserId = ref('')

const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '待派单 WAITING_ASSIGNMENT', value: 'WAITING_ASSIGNMENT' },
  { label: '已派单 ASSIGNED', value: 'ASSIGNED' },
  { label: '已接单 ACCEPTED', value: 'ACCEPTED' },
  { label: '已取货 PICKED', value: 'PICKED' },
  { label: '已送达 DELIVERED', value: 'DELIVERED' }
]

async function load() {
  loading.value = true
  try {
    tasks.value = await adminApi.deliveryTasks(status.value || undefined)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function openAssign(row: DeliveryTask) {
  current.value = row
  riderUserId.value = ''
  dialogVisible.value = true
}

/** 派单需要骑手用户 ID；接单超时由平台规则 delivery.accept.timeout.minutes 控制 */
async function confirmAssign() {
  if (!current.value) return
  if (!riderUserId.value.trim()) {
    ElMessage.warning('请填写骑手用户 ID')
    return
  }
  assigning.value = true
  try {
    await adminApi.assignDeliveryTask(current.value.id, { riderUserId: Number(riderUserId.value) })
    ElMessage.success('已派单')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    assigning.value = false
  }
}

function statusTagType(value: string) {
  if (value === 'DELIVERED') return 'success'
  if (value === 'WAITING_ASSIGNMENT') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<template>
  <section class="dispatch">
    <header class="page-head">
      <h2>配送派单</h2>
      <span class="sub">按区域派单；骑手接单超时后任务自动回到待派单池</span>
    </header>

    <div class="dispatch__toolbar">
      <el-select v-model="status" class="dispatch__filter" @change="load">
        <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="dispatch__panel">
      <el-table v-loading="loading" :data="tasks" size="small">
        <el-table-column prop="id" label="任务" width="80" />
        <el-table-column prop="orderId" label="订单" width="90" />
        <el-table-column prop="merchantId" label="商家" width="90" />
        <el-table-column prop="deliveryZoneId" label="区域" width="90" />
        <el-table-column label="状态" width="170">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接单截止" min-width="160">
          <template #default="{ row }">{{ row.acceptDeadlineAt ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="骑手" width="100">
          <template #default="{ row }">{{ row.riderUserId ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openAssign(row)">派单</el-button>
          </template>
        </el-table-column>
        <template #empty>当前筛选下没有配送任务</template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="指派骑手" width="460px">
      <el-alert
        v-if="current"
        type="info"
        :closable="false"
        :title="`任务 #${current.id} · 订单 #${current.orderId} · 区域 #${current.deliveryZoneId}`"
        class="dispatch__tip"
      />
      <el-form label-position="top">
        <el-form-item label="骑手用户 ID" required>
          <el-input v-model="riderUserId" placeholder="如 12（rider-test-01 的用户 ID）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="confirmAssign">确认派单</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.dispatch {
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
    width: 260px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__tip {
    margin-bottom: 14px;
  }
}
</style>
