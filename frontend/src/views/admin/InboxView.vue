<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api/admin'
import { errorMessage } from '../../api/http'

const sending = ref(false)
const form = ref({ userId: '', title: '', content: '' })

async function send() {
  if (!form.value.userId || !form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('用户 ID、标题与内容均为必填')
    return
  }
  sending.value = true
  try {
    await adminApi.sendInboxMessage({
      userId: Number(form.value.userId),
      title: form.value.title.trim(),
      content: form.value.content.trim()
    })
    ElMessage.success('站内消息已发送')
    form.value = { userId: '', title: '', content: '' }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="admin-inbox">
    <header class="page-head">
      <h2>站内消息</h2>
      <span class="sub">后台定向发送；节气与节日 SVG 卡片走独立的卡片任务</span>
    </header>

    <el-card shadow="never" class="admin-inbox__panel">
      <template #header>发送消息</template>
      <el-form label-position="top" class="admin-inbox__form">
        <el-form-item label="用户 ID" required>
          <el-input v-model="form.userId" placeholder="userId" />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="消息标题" />
        </el-form-item>
        <el-form-item label="内容" required class="admin-inbox__span">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="消息正文" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.admin-inbox {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 16px;
    max-width: 760px;
  }

  &__span {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .admin-inbox__form {
    grid-template-columns: 1fr;
  }
}
</style>
