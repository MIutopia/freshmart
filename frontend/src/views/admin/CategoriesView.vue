<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi, type CategoryView } from '../../api/admin'
import { errorMessage } from '../../api/http'

const categories = ref<CategoryView[]>([])
const loading = ref(false)
const creating = ref(false)
const form = ref({ parentId: '', name: '', sortOrder: '0' })

async function load() {
  loading.value = true
  try {
    categories.value = await adminApi.listCategories()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function createCategory() {
  if (!form.value.name.trim()) {
    ElMessage.warning('分类名称必填')
    return
  }
  creating.value = true
  try {
    await adminApi.createCategory({
      parentId: form.value.parentId ? Number(form.value.parentId) : undefined,
      name: form.value.name.trim(),
      sortOrder: Number(form.value.sortOrder || 0)
    })
    ElMessage.success('分类已创建')
    form.value = { parentId: '', name: '', sortOrder: '0' }
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
  <section class="categories">
    <header class="page-head">
      <h2>商品分类</h2>
      <span class="sub">分类是商品上架与仓库经营范围的基础；未配置分类仓配规则的商品不可上架</span>
    </header>

    <div class="categories__grid">
      <el-card shadow="never" class="categories__panel">
        <template #header>新建分类</template>
        <el-form label-position="top">
          <el-form-item label="父分类 ID">
            <el-input v-model="form.parentId" placeholder="留空表示一级分类" />
          </el-form-item>
          <el-form-item label="分类名称" required>
            <el-input v-model="form.name" placeholder="如 叶菜类" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input v-model="form.sortOrder" placeholder="0" />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="creating" @click="createCategory">创建分类</el-button>
        <p class="categories__hint">
          创建后可在商家端「仓库与分类规则」中配置仓库经营分类与分类仓配规则。
        </p>
      </el-card>

      <el-card shadow="never" class="categories__panel">
        <template #header>
          <div class="categories__header">
            <span>全部分类（{{ categories.length }}）</span>
            <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
          </div>
        </template>
        <el-table v-loading="loading" :data="categories" size="small">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column label="父分类" width="100">
            <template #default="{ row }">{{ row.parentId ?? '一级' }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="90" />
          <template #empty>暂无分类</template>
        </el-table>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.categories {
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

  &__hint {
    margin: 14px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: $text-muted;
  }
}

@media (max-width: 960px) {
  .categories__grid {
    grid-template-columns: 1fr;
  }
}
</style>
