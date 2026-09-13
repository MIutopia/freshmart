<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { inboxApi, type InboxMessage } from '../../api/afterSale'
import { errorMessage } from '../../api/http'

const messages = ref<InboxMessage[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    messages.value = await inboxApi.list()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function markRead(message: InboxMessage) {
  try {
    await inboxApi.markRead(message.id)
    message.read = true
    ElMessage.success('已标记为已读')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

onMounted(load)
</script>

<template>
  <section class="inbox">
    <header class="page-head">
      <h2>站内消息</h2>
      <span class="sub">含节气 / 节日 SVG 卡片推送；可在“账号与偏好”中关闭</span>
    </header>

    <div class="inbox__toolbar">
      <span class="inbox__count">共 {{ messages.length }} 条</span>
      <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated class="inbox__skeleton" />

    <el-empty v-else-if="messages.length === 0" description="暂无消息" />

    <div v-else class="inbox__list">
      <article v-for="message in messages" :key="message.id" class="inbox-card">
        <header class="inbox-card__head">
          <h3 class="inbox-card__title">{{ message.title }}</h3>
          <el-tag v-if="!message.read" type="warning" size="small" effect="light">未读</el-tag>
          <span class="inbox-card__time">{{ message.createdAt }}</span>
        </header>

        <p class="inbox-card__content">{{ message.content }}</p>

        <!-- 卡片 SVG 由后端内容策略校验后下发，仅作展示 -->
        <img v-if="message.cardSvgUrl" :src="message.cardSvgUrl" alt="节日卡片" class="inbox-card__card" />
        <div v-else-if="message.cardSvgContent" class="inbox-card__card" v-html="message.cardSvgContent"></div>

        <footer v-if="!message.read" class="inbox-card__footer">
          <el-button size="small" @click="markRead(message)">标记已读</el-button>
        </footer>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.inbox {
  @include page-shell;

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    margin-bottom: 16px;
    @include panel;
  }

  &__count {
    font-size: 13px;
    color: $text-secondary;
  }

  &__skeleton {
    padding: 20px;
    @include panel;
  }

  &__list {
    display: grid;
    gap: 14px;
  }
}

.inbox-card {
  padding: 18px;
  @include panel;
  @include card-hover;

  &__head {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__title {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
    color: $text-primary;
  }

  &__time {
    margin-left: auto;
    font-size: 12px;
    color: $text-muted;
  }

  &__content {
    margin: 10px 0 0;
    font-size: 13px;
    line-height: 1.8;
    color: $text-secondary;
  }

  &__card {
    display: block;
    width: 100%;
    max-width: 320px;
    margin-top: 12px;
    border: 1px solid $border;
    border-radius: $radius-md;
  }

  &__footer {
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px solid $border;
  }
}
</style>
