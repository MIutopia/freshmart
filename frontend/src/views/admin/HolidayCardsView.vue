<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'

interface HolidayCardTask {
  id: number
  taskKey?: string
  holidayKey?: string
  greeting?: string
  status?: string
  sentCount?: number
  failedCount?: number
  scheduledAt?: string
  [key: string]: unknown
}

const tasks = ref<HolidayCardTask[]>([])
const loading = ref(false)
const creating = ref(false)
const runningId = ref<number | null>(null)

const form = ref({ taskKey: '', holidayKey: '', greeting: '', scheduledAt: '' })

async function load() {
  loading.value = true
  try {
    tasks.value = (await adminApi.holidayCardTasks()) as unknown as HolidayCardTask[]
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function createTask() {
  const { taskKey, holidayKey, greeting, scheduledAt } = form.value
  if (!taskKey.trim() || !holidayKey.trim() || !greeting.trim() || !scheduledAt) {
    ElMessage.warning('任务键、节日键、问候语与计划时间均为必填')
    return
  }
  creating.value = true
  try {
    await adminApi.createHolidayCardTask({
      taskKey: taskKey.trim(),
      holidayKey: holidayKey.trim(),
      greeting: greeting.trim(),
      scheduledAt
    })
    ElMessage.success('卡片任务已创建')
    form.value = { taskKey: '', holidayKey: '', greeting: '', scheduledAt: '' }
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    creating.value = false
  }
}

async function runTask(taskId: number) {
  runningId.value = taskId
  try {
    await adminApi.runHolidayCardTask(taskId)
    ElMessage.success('任务已执行；失败投递会在下一轮自动重试')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    runningId.value = null
  }
}

function statusTagType(status?: string) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<template>
  <section class="holiday-cards">
    <header class="page-head">
      <h2>节气卡片任务</h2>
      <span class="sub">定时投递站内消息；已完成的任务不再接受手动运行</span>
    </header>

    <el-card shadow="never" class="holiday-cards__panel holiday-cards__create">
      <template #header>新建任务</template>
      <el-form label-position="top" class="holiday-cards__form">
        <el-form-item label="任务键" required>
          <el-input v-model="form.taskKey" placeholder="如 2026-mid-autumn" />
        </el-form-item>
        <el-form-item label="节日键" required>
          <el-input v-model="form.holidayKey" placeholder="如 MID_AUTUMN" />
        </el-form-item>
        <el-form-item label="问候语" required>
          <el-input v-model="form.greeting" placeholder="受限文案，最长 120 字" />
        </el-form-item>
        <el-form-item label="计划时间" required>
          <el-date-picker v-model="form.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="creating" @click="createTask">创建任务</el-button>
    </el-card>

    <el-card shadow="never" class="holiday-cards__panel">
      <template #header>
        <div class="holiday-cards__header">
          <span>最近任务（{{ tasks.length }}）</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskKey" label="任务键" min-width="150" />
        <el-table-column prop="holidayKey" label="节日" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="statusTagType(row.status)">{{ row.status ?? '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投递统计" width="150">
          <template #default="{ row }">成功 {{ row.sentCount ?? 0 }} / 失败 {{ row.failedCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="计划时间" min-width="160">
          <template #default="{ row }">{{ row.scheduledAt ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              size="small"
              :loading="runningId === row.id"
              :disabled="row.status === 'COMPLETED'"
              @click="runTask(Number(row.id))"
            >
              立即执行
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无卡片任务</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.holiday-cards {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__create {
    margin-bottom: 16px;
  }

  &__form {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0 16px;
    max-width: 1100px;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    color: $text-secondary;
  }
}

@media (max-width: 1280px) {
  .holiday-cards__form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .holiday-cards__form {
    grid-template-columns: 1fr;
  }
}
</style>
