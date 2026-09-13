<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi, type DeliveryZoneView } from '../../api/admin'
import { errorMessage } from '../../api/http'

const zones = ref<DeliveryZoneView[]>([])
const loading = ref(false)
const creating = ref(false)

/** 后端 CreateZoneRequest：name / areaCode / boundaryJson */
const form = ref({ name: '', areaCode: '', boundaryJson: '' })

async function load() {
  loading.value = true
  try {
    zones.value = await adminApi.deliveryZones()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function createZone() {
  if (!form.value.name.trim() || !form.value.areaCode.trim()) {
    ElMessage.warning('区域名称与区域编码均为必填')
    return
  }
  creating.value = true
  try {
    await adminApi.createDeliveryZone({
      name: form.value.name.trim(),
      areaCode: form.value.areaCode.trim(),
      boundaryJson: form.value.boundaryJson.trim() || undefined
    })
    ElMessage.success('配送区域已创建')
    form.value = { name: '', areaCode: '', boundaryJson: '' }
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="zones">
    <header class="page-head">
      <h2>配送区域</h2>
      <span class="sub">固定配送区域；商家仓库必须绑定启用的区域</span>
    </header>

    <div class="zones__grid">
      <el-card shadow="never" class="zones__panel">
        <template #header>新建区域</template>
        <el-form label-position="top">
          <el-form-item label="区域名称" required>
            <el-input v-model="form.name" placeholder="如 城东片区" />
          </el-form-item>
          <el-form-item label="区域编码" required>
            <el-input v-model="form.areaCode" placeholder="如 ZONE-EAST" />
          </el-form-item>
          <el-form-item label="边界 JSON">
            <el-input v-model="form.boundaryJson" type="textarea" :rows="3" placeholder="可选，GeoJSON 边界" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="creating" @click="createZone">创建区域</el-button>
      </el-card>

      <el-card shadow="never" class="zones__panel">
        <template #header>
          <div class="zones__header">
            <span>现有区域（{{ zones.length }}）</span>
            <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
          </div>
        </template>
        <el-table v-loading="loading" :data="zones" size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="130" />
          <el-table-column prop="areaCode" label="编码" width="130" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="light" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>暂无配送区域</template>
        </el-table>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.zones {
  @include page-shell;

  &__grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
    gap: 16px;
  }

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

@media (max-width: 960px) {
  .zones__grid {
    grid-template-columns: 1fr;
  }
}
</style>
