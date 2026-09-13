<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { catalogApi, type Product } from '../../api/catalog'
import { errorMessage } from '../../api/http'

const products = ref<Product[]>([])
const loading = ref(false)
const creating = ref(false)
const publishingId = ref<number | null>(null)
const formVisible = ref(false)

const form = ref({ categoryId: '', name: '', description: '', marketPricePerKg: '', merchantPricePerKg: '' })

async function load() {
  loading.value = true
  try {
    products.value = await catalogApi.listMine()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function createProduct() {
  if (!form.value.categoryId || !form.value.name || !form.value.marketPricePerKg || !form.value.merchantPricePerKg) {
    ElMessage.warning('分类、名称、市场价与商家价均为必填')
    return
  }
  creating.value = true
  try {
    await catalogApi.createProduct({
      categoryId: Number(form.value.categoryId),
      name: form.value.name.trim(),
      description: form.value.description.trim() || undefined,
      marketPricePerKg: Number(form.value.marketPricePerKg),
      merchantPricePerKg: Number(form.value.merchantPricePerKg)
    })
    ElMessage.success('商品草稿已创建，需配置分类仓配规则后上架')
    formVisible.value = false
    form.value = { categoryId: '', name: '', description: '', marketPricePerKg: '', merchantPricePerKg: '' }
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    creating.value = false
  }
}

async function publish(productId: number) {
  publishingId.value = productId
  try {
    await catalogApi.publishProduct(productId)
    ElMessage.success('商品已上架')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    publishingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="merchant-products">
    <header class="page-head">
      <h2>商品管理</h2>
      <span class="sub">商家价不得高于市场价 5%；上架需存在启用的分类仓配规则</span>
    </header>

    <el-card shadow="never" class="merchant-products__panel">
      <template #header>
        <div class="merchant-products__header">
          <span>共 {{ products.length }} 个商品</span>
          <div class="merchant-products__actions">
            <el-button type="primary" @click="formVisible = true">新建商品</el-button>
            <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="products" size="small">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="分类" width="90">
          <template #default="{ row }">#{{ row.categoryId }}</template>
        </el-table-column>
        <el-table-column label="商家价" width="110">
          <template #default="{ row }">¥{{ row.merchantPricePerKg }}</template>
        </el-table-column>
        <el-table-column label="市场价" width="110">
          <template #default="{ row }">¥{{ row.marketPricePerKg }}</template>
        </el-table-column>
        <el-table-column label="可售" width="100">
          <template #default="{ row }">{{ row.availableGrams }} g</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :loading="publishingId === row.id" @click="publish(row.id)">
              上架
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无商品</template>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" title="新建商品草稿" width="560px">
      <el-form label-position="top">
        <el-form-item label="分类 ID" required>
          <el-input v-model="form.categoryId" placeholder="categoryId" />
        </el-form-item>
        <el-form-item label="商品名称" required>
          <el-input v-model="form.name" placeholder="如 本地番茄" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="可选" />
        </el-form-item>
        <el-form-item label="市场价（元/kg）" required>
          <el-input v-model="form.marketPricePerKg" placeholder="10.00" />
        </el-form-item>
        <el-form-item label="商家价（元/kg）" required>
          <el-input v-model="form.merchantPricePerKg" placeholder="9.50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createProduct">创建草稿</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.merchant-products {
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
}
</style>
