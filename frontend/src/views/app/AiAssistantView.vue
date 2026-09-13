<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { aiApi } from '../../api/ai'
import { errorMessage } from '../../api/http'

interface Bubble {
  role: 'user' | 'ai'
  text: string
}

const bubbles = ref<Bubble[]>([])
const input = ref('')
const sending = ref(false)

const EXAMPLES = ['今晚想做番茄炒蛋，预算 30 元', '帮我查最近一笔订单的状态', '推荐两款适合凉拌的蔬菜']

function replyText(payload: Record<string, unknown>): string {
  const value = payload.reply ?? payload.answer ?? payload.content ?? payload.message
  if (typeof value === 'string' && value.trim()) return value
  return JSON.stringify(payload, null, 2)
}

async function send(preset?: string) {
  const text = (preset ?? input.value).trim()
  if (!text || sending.value) return

  bubbles.value.push({ role: 'user', text })
  input.value = ''
  sending.value = true

  try {
    const response = await aiApi.assistant({ message: text })
    bubbles.value.push({ role: 'ai', text: replyText(response as Record<string, unknown>) })
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="assistant">
    <header class="page-head">
      <h2>AI 导购</h2>
      <span class="sub">工具范围由后端固定：只读商品摘要与本人订单，不跨账户</span>
    </header>

    <el-card shadow="never" class="assistant__panel">
      <div class="assistant__body">
        <el-empty v-if="bubbles.length === 0" description="描述你的需求，例如忌口、预算或想做的菜">
          <div class="assistant__examples">
            <el-tag
              v-for="example in EXAMPLES"
              :key="example"
              class="assistant__example"
              effect="plain"
              @click="send(example)"
            >
              {{ example }}
            </el-tag>
          </div>
        </el-empty>

        <div v-else class="assistant__bubbles">
          <div
            v-for="(bubble, index) in bubbles"
            :key="index"
            class="assistant__bubble"
            :class="`assistant__bubble--${bubble.role}`"
          >
            <span class="assistant__bubble-role">{{ bubble.role === 'user' ? '我' : 'AI 导购' }}</span>
            <p class="assistant__bubble-text">{{ bubble.text }}</p>
          </div>
        </div>
      </div>

      <div class="assistant__composer">
        <el-input
          v-model="input"
          placeholder="描述需求，按回车发送"
          :disabled="sending"
          @keyup.enter="send()"
        />
        <el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!input.trim()" @click="send()">
          发送
        </el-button>
      </div>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.assistant {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__body {
    min-height: 300px;
  }

  &__examples {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
  }

  &__example {
    cursor: pointer;
  }

  &__bubbles {
    display: grid;
    gap: 14px;
  }

  &__bubble {
    max-width: 72%;
    padding: 12px 14px;
    border-radius: $radius-md;
    border: 1px solid $border;

    &--user {
      margin-left: auto;
      background: $mint-soft;
      border-color: rgba($mint, 0.3);
    }

    &--ai {
      background: $surface;
    }
  }

  &__bubble-role {
    font-size: 12px;
    color: $text-muted;
  }

  &__bubble-text {
    margin: 6px 0 0;
    font-size: 13.5px;
    line-height: 1.8;
    color: $text-primary;
    white-space: pre-wrap;
  }

  &__composer {
    display: flex;
    gap: 10px;
    margin-top: 18px;
    padding-top: 16px;
    border-top: 1px solid $border;
  }
}
</style>
