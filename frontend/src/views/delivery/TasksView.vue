<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { deliveryApi, type DeliveryTask } from '../../api/delivery'
import { errorMessage } from '../../api/http'
import { DELIVERY_TASK_STATUS, labelOf, tagTypeOf } from '../../constants/dictionaries'

const tasks = ref<DeliveryTask[]>([])
const loading = ref(false)
const busyId = ref<number | null>(null)
const deliverVisible = ref(false)
const deliverTaskId = ref<number | null>(null)
const proofUrl = ref('')

async function load() {
  loading.value = true
  try {
    tasks.value = await deliveryApi.tasks()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function act(taskId: number, action: 'accept' | 'pick') {
  busyId.value = taskId
  try {
    if (action === 'accept') await deliveryApi.accept(taskId)
    else await deliveryApi.pick(taskId)
    ElMessage.success(action === 'accept' ? '已接单' : '已确认取货')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busyId.value = null
  }
}

function openDeliver(taskId: number) {
  deliverTaskId.value = taskId
  proofUrl.value = ''
  deliverVisible.value = true
}

/** 送达必须提交凭证 URL，后端 DeliverRequest.proofUrl 必填 */
async function confirmDeliver() {
  if (!deliverTaskId.value) return
  if (!proofUrl.value.trim()) {
    ElMessage.warning('请填写送达凭证 URL')
    return
  }
  busyId.value = deliverTaskId.value
  try {
    await deliveryApi.deliver(deliverTaskId.value, { proofUrl: proofUrl.value.trim() })
    ElMessage.success('已标记送达')
    deliverVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="delivery-tasks">
    <header class="page-head">
      <h2>配送任务</h2>
      <span class="sub">接单超时（平台规则 delivery.accept.timeout.minutes）后任务自动回到派单池</span>
    </header>

    <el-card shadow="never" class="delivery-tasks__panel">
      <template #header>
        <div class="delivery-tasks__header">
          <span>共 {{ tasks.length }} 个任务</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" size="small">
        <el-table-column prop="id" label="任务" width="80" />
        <el-table-column prop="orderId" label="订单" width="90" />
        <el-table-column prop="merchantId" label="商家" width="90" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="tagTypeOf(DELIVERY_TASK_STATUS, row.status)">
              {{ labelOf(DELIVERY_TASK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接单截止" min-width="160">
          <template #default="{ row }">{{ row.acceptDeadlineAt ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :loading="busyId === row.id" @click="act(row.id, 'accept')">
              接单
            </el-button>
            <el-button text type="primary" size="small" :loading="busyId === row.id" @click="act(row.id, 'pick')">
              取货
            </el-button>
            <el-button text type="primary" size="small" :loading="busyId === row.id" @click="openDeliver(row.id)">
              送达
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无配送任务</template>
      </el-table>
    </el-card>

    <el-dialog v-model="deliverVisible" title="上传送达凭证" width="520px">
      <el-form label-position="top">
        <el-form-item label="凭证图片 URL" required>
          <el-input v-model="proofUrl" placeholder="先通过 /api/media/upload 上传后填入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverVisible = false">取消</el-button>
        <el-button type="primary" :loading="busyId === deliverTaskId" @click="confirmDeliver">确认送达</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.delivery-tasks {
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
