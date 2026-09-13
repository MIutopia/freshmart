<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { notificationPreferenceApi } from '../../api/afterSale'
import { errorMessage } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const seasonalCardEnabled = ref(true)
const loaded = ref(false)
const saving = ref(false)

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

onMounted(load)
</script>

<template>
  <section class="profile">
    <header class="page-head">
      <h2>账号与偏好</h2>
      <span class="sub">登录账号信息与节气卡片推送设置</span>
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
}

@media (max-width: 960px) {
  .profile__grid {
    grid-template-columns: 1fr;
  }
}
</style>
