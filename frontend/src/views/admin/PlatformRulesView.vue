<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi, type PlatformRule } from '../../api/admin'
import { errorMessage } from '../../api/http'
import { VALUE_TYPE, labelOf, tagTypeOf } from '../../constants/dictionaries'

const rules = ref<PlatformRule[]>([])
const drafts = ref<Record<string, string>>({})
const loading = ref(false)
const savingKey = ref<string | null>(null)

async function load() {
  loading.value = true
  try {
    rules.value = await adminApi.platformRules()
    drafts.value = Object.fromEntries(rules.value.map((rule) => [rule.ruleKey, rule.ruleValue]))
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function save(rule: PlatformRule) {
  const value = drafts.value[rule.ruleKey]
  if (value === undefined || value === rule.ruleValue) {
    ElMessage.info('规则值未变化')
    return
  }
  savingKey.value = rule.ruleKey
  try {
    await adminApi.updatePlatformRule(rule.ruleKey, value)
    ElMessage.success(`${rule.ruleKey} 已更新`)
    await load()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    savingKey.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="platform-rules">
    <header class="page-head">
      <h2>平台规则</h2>
      <span class="sub">配送费、库存预占、称重误差、积分与接单超时等运行时可调参数</span>
    </header>

    <el-card shadow="never" class="platform-rules__panel">
      <template #header>
        <div class="platform-rules__header">
          <span>共 {{ rules.length }} 条规则</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rules" size="small">
        <el-table-column prop="ruleKey" label="规则键" min-width="220" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="tagTypeOf(VALUE_TYPE, row.valueType)">
              {{ labelOf(VALUE_TYPE, row.valueType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="值" width="220">
          <template #default="{ row }">
            <el-input v-model="drafts[row.ruleKey]" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              size="small"
              :loading="savingKey === row.ruleKey"
              @click="save(row)"
            >
              保存
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无规则</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.platform-rules {
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
