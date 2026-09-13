<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'

const creating = ref(false)
const created = ref<Array<{ id: number; name: string }>>([])
const form = ref({ parentId: '', name: '', sortOrder: '0' })

async function createCategory() {
  if (!form.value.name.trim()) {
    ElMessage.warning('分类名称必填')
    return
  }
  creating.value = true
  try {
    const result = await adminApi.createCategory({
      parentId: form.value.parentId ? Number(form.value.parentId) : undefined,
      name: form.value.name.trim(),
      sortOrder: Number(form.value.sortOrder || 0)
    })
    created.value.unshift({ id: result.id, name: form.value.name.trim() })
    ElMessage.success(`分类已创建（ID ${result.id}）`)
    form.value = { parentId: '', name: '', sortOrder: '0' }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <section class="categories">
    <header class="page-head">
      <h2>商品分类</h2>
      <span class="sub">分类是商品上架与仓库经营范围的基础；未配置分类仓配规则的商品不可上架</span>
    </header>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="暂未提供分类列表接口"
      description="后端目前仅开放创建分类；下方为本次会话内创建的记录，刷新页面后清空。"
      class="categories__tip"
    />

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
      </el-card>

      <el-card shadow="never" class="categories__panel">
        <template #header>本次创建</template>
        <el-table :data="created" size="small">
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <template #empty>本次尚未创建分类</template>
        </el-table>
        <p class="categories__hint">创建后可在商家端“仓库与分类规则”中配置仓库经营分类与分类仓配规则。</p>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.categories {
  @include page-shell;

  &__tip {
    margin-bottom: 16px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
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
