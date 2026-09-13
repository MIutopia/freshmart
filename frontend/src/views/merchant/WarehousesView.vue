<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { catalogApi } from '../../api/catalog'
import { merchantApi, type MerchantWarehouse } from '../../api/merchant'
import { errorMessage } from '../../api/http'

const warehouses = ref<MerchantWarehouse[]>([])
const loading = ref(false)
const creating = ref(false)
const savingRule = ref(false)

const form = ref({ deliveryZoneId: '', name: '', code: '', address: '' })
const ruleForm = ref({ warehouseId: '', categoryId: '', priority: '0' })

async function load() {
  loading.value = true
  try {
    warehouses.value = await merchantApi.warehouses()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function createWarehouse() {
  if (!form.value.deliveryZoneId || !form.value.name || !form.value.code || !form.value.address) {
    ElMessage.warning('配送区域、名称、编码与地址均为必填')
    return
  }
  creating.value = true
  try {
    await catalogApi.createWarehouse({
      deliveryZoneId: Number(form.value.deliveryZoneId),
      name: form.value.name.trim(),
      code: form.value.code.trim(),
      address: form.value.address.trim()
    })
    ElMessage.success('仓库已创建，请继续配置可经营分类与分类仓配规则')
    form.value = { deliveryZoneId: '', name: '', code: '', address: '' }
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    creating.value = false
  }
}

/** 同时写入仓库可经营分类与分类仓配规则，两者齐备后商品才允许上架 */
async function saveCategoryRule() {
  if (!ruleForm.value.warehouseId || !ruleForm.value.categoryId) {
    ElMessage.warning('仓库与分类均为必填')
    return
  }
  savingRule.value = true
  try {
    const warehouseId = Number(ruleForm.value.warehouseId)
    const categoryId = Number(ruleForm.value.categoryId)
    await catalogApi.allowWarehouseCategory({ warehouseId, categoryId })
    await catalogApi.createWarehouseRule({ warehouseId, categoryId, priority: Number(ruleForm.value.priority || 0) })
    ElMessage.success('分类仓配规则已保存')
    ruleForm.value = { warehouseId: '', categoryId: '', priority: '0' }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    savingRule.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="warehouses">
    <header class="page-head">
      <h2>仓库与分类规则</h2>
      <span class="sub">仓库必须绑定启用的固定配送区域；上架前需同时配置可经营分类与分类仓配规则</span>
    </header>

    <div class="warehouses__grid">
      <el-card shadow="never" class="warehouses__panel">
        <template #header>新建仓库</template>
        <el-form label-position="top">
          <el-form-item label="配送区域 ID" required>
            <el-input v-model="form.deliveryZoneId" placeholder="deliveryZoneId" />
          </el-form-item>
          <el-form-item label="仓库名称" required>
            <el-input v-model="form.name" placeholder="如 城东一仓" />
          </el-form-item>
          <el-form-item label="仓库编码" required>
            <el-input v-model="form.code" placeholder="如 WH-001" />
          </el-form-item>
          <el-form-item label="地址" required>
            <el-input v-model="form.address" placeholder="详细地址" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="creating" @click="createWarehouse">创建仓库</el-button>
      </el-card>

      <el-card shadow="never" class="warehouses__panel">
        <template #header>配置分类仓配规则</template>
        <el-form label-position="top">
          <el-form-item label="仓库 ID" required>
            <el-input v-model="ruleForm.warehouseId" placeholder="warehouseId" />
          </el-form-item>
          <el-form-item label="分类 ID" required>
            <el-input v-model="ruleForm.categoryId" placeholder="categoryId" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input v-model="ruleForm.priority" placeholder="0" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="savingRule" @click="saveCategoryRule">保存规则</el-button>
      </el-card>
    </div>

    <el-card shadow="never" class="warehouses__panel warehouses__list">
      <template #header>
        <div class="warehouses__header">
          <span>我的仓库（{{ warehouses.length }}）</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="warehouses" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="code" label="编码" width="110" />
        <el-table-column prop="deliveryZoneId" label="配送区域" width="100" />
        <el-table-column prop="address" label="地址" min-width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>暂无仓库</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.warehouses {
  @include page-shell;

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    margin-bottom: 16px;
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
  .warehouses__grid {
    grid-template-columns: 1fr;
  }
}
</style>
