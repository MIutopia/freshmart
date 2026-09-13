<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { holidayCardApi, notificationPreferenceApi, type HolidayCardPreview } from '../../api/afterSale'
import { errorMessage } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const seasonalCardEnabled = ref(true)
const loaded = ref(false)
const saving = ref(false)

const previewForm = ref({ holidayKey: '立冬', greeting: '节气将至，愿新鲜常伴' })
const previewing = ref(false)
const preview = ref<HolidayCardPreview | null>(null)

async function load() {
  try {
    const preference = await notificationPreferenceApi.get()
    seasonalCardEnabled.value = preference.seasonalCardEnabled
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loaded.value = true
  }
}

async function save() {
  saving.value = true
  try {
    await notificationPreferenceApi.update({ seasonalCardEnabled: seasonalCardEnabled.value })
    ElMessage.success('偏好已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

async function loadPreview() {
  previewing.value = true
  try {
    preview.value = await holidayCardApi.preview(
      previewForm.value.holidayKey.trim() || undefined,
      previewForm.value.greeting.trim() || undefined
    )
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    previewing.value = false
  }
}

onMounted(async () => {
  await load()
  await loadPreview()
})
</script>

<template>
  <section class="profile">
    <header class="page-head">
      <h2>账号与偏好</h2>
      <span class="sub">登录账号信息、节气卡片推送开关与卡片样式预览</span>
    </header>

    <div class="profile__grid">
      <el-card shadow="never" class="profile__panel">
        <template #header>账号</template>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="用户 ID">{{ auth.profile?.userId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="登录名">{{ auth.loginName }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag v-for="role in auth.roles" :key="role" size="small" effect="light" class="profile__role">
              {{ role }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="profile__panel">
        <template #header>节气 / 节日卡片推送</template>
        <p class="profile__hint">关闭后不再接收节气与节日 SVG 卡片站内消息。</p>
        <el-switch
          v-model="seasonalCardEnabled"
          :disabled="!loaded"
          active-text="接收节气 / 节日卡片"
          inline-prompt
        />
        <div class="profile__actions">
          <el-button type="primary" :loading="saving" :disabled="!loaded" @click="save">保存</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="profile__panel profile__panel--wide">
        <template #header>卡片预览</template>
        <p class="profile__hint">
          预览只渲染样式，不会产生站内消息；实际推送由平台按节气任务定时发送。
        </p>
        <div class="profile__preview-form">
          <el-form label-position="top" class="profile__preview-fields">
            <el-form-item label="节气 / 节日">
              <el-input v-model="previewForm.holidayKey" placeholder="如 立冬" />
            </el-form-item>
            <el-form-item label="祝福语">
              <el-input v-model="previewForm.greeting" placeholder="如 节气将至，愿新鲜常伴" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="previewing" @click="loadPreview">刷新预览</el-button>
        </div>
        <!-- svg 由后端 HolidayCardContentPolicy 转义后返回，可直接内联 -->
        <div v-if="preview" class="profile__preview-card" v-html="preview.svg" />
        <el-empty v-else description="暂无预览" />
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.profile {
  @include page-shell;

  &__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;

    &--wide {
      grid-column: 1 / -1;
    }
  }

  &__role {
    margin-right: 6px;
  }

  &__hint {
    margin: 0 0 16px;
    font-size: 13px;
    color: $text-secondary;
  }

  &__actions {
    margin-top: 18px;
  }

  &__preview-form {
    display: flex;
    align-items: flex-end;
    gap: 16px;
    margin-bottom: 18px;
  }

  &__preview-fields {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 16px;
    flex: 1;
  }

  &__preview-card {
    max-width: 640px;

    :deep(svg) {
      width: 100%;
      height: auto;
      border-radius: $radius-lg;
      border: 1px solid $border;
    }
  }
}

@media (max-width: 960px) {
  .profile__grid,
  .profile__preview-fields {
    grid-template-columns: 1fr;
  }

  .profile__preview-form {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
