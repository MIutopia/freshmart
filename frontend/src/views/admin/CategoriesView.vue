<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi, type CategoryView } from '../../api/admin'
import { errorMessage } from '../../api/http'

const categories = ref<CategoryView[]>([])
const loading = ref(false)
const creating = ref(false)
const saving = ref(false)

const form = ref({ parentId: '', name: '', sortOrder: '0' })

const editVisible = ref(false)
const editForm = ref({ id: 0, name: '', sortOrder: '0', status: 'ACTIVE' })

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

function openEdit(row: CategoryView) {
  editForm.value = {
    id: row.id,
    name: row.name,
    sortOrder: String(row.sortOrder),
    status: row.status
  }
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.value.name.trim()) {
    ElMessage.warning('分类名称必填')
    return
  }
  saving.value = true
  try {
    await adminApi.updateCategory(editForm.value.id, {
      name: editForm.value.name.trim(),
      sortOrder: Number(editForm.value.sortOrder || 0),
      status: editForm.value.status
    })
    ElMessage.success('分类已更新')
    editVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

/** 停用走同一更新接口；分类下仍有在架商品时后端拒绝并返回冲突 */
async function toggleStatus(row: CategoryView) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await adminApi.updateCategory(row.id, {
      name: row.name,
      sortOrder: row.sortOrder,
      status: next
    })
    ElMessage.success(next === 'ACTIVE' ? '分类已启用' : '分类已停用')
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

onMounted(load)
</script>

<template>
  <section class="categories">
    <header class="page-head">
      <h2>商品分类</h2>
      <span class="sub">
        分类是商品上架与仓库经营范围的基础；停用前需先下架该分类下的全部商品
      </span>
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
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="130" />
          <el-table-column label="父分类" width="90">
            <template #default="{ row }">{{ row.parentId ?? '一级' }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="light" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button
                text
                size="small"
                :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>暂无分类</template>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="editVisible" title="编辑分类" width="460px">
      <el-form label-position="top">
        <el-form-item label="分类名称" required>
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input v-model="editForm.sortOrder" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="启用 ACTIVE" value="ACTIVE" />
            <el-option label="停用 INACTIVE" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.categories {
  @include page-shell;

  &__grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.5fr);
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
