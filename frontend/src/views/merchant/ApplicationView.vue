<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '../../api/merchant'
import { errorMessage } from '../../api/http'

const submitting = ref(false)
const form = ref({ merchantName: '', businessLicenseUrl: '' })

async function submit() {
  if (!form.value.merchantName.trim()) {
    ElMessage.warning('店铺名称必填')
    return
  }
  submitting.value = true
  try {
    await merchantApi.submitApplication({
      merchantName: form.value.merchantName.trim(),
      businessLicenseUrl: form.value.businessLicenseUrl.trim() || undefined
    })
    ElMessage.success('入驻申请已提交，等待平台审核通过后即可营业')
    form.value = { merchantName: '', businessLicenseUrl: '' }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="merchant-application">
    <header class="page-head">
      <h2>入驻资料</h2>
      <span class="sub">商家先审核后营业；审核通过才会生成可用的商家档案</span>
    </header>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="入驻申请由消费者账号提交"
      description="后端要求申请者为 CONSUMER 角色：消费者提交申请、平台审核通过后升级为商家。"
      class="merchant-application__tip"
    />

    <el-card shadow="never" class="merchant-application__panel">
      <template #header>提交入驻申请</template>
      <el-form label-position="top" class="merchant-application__form">
        <el-form-item label="店铺名称" required>
          <el-input v-model="form.merchantName" placeholder="如 鲜达果蔬" />
        </el-form-item>
        <el-form-item label="营业执照图片 URL">
          <el-input v-model="form.businessLicenseUrl" placeholder="先通过 /api/media/upload 上传后填入" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.merchant-application {
  @include page-shell;

  &__tip {
    margin-bottom: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__form {
    max-width: 520px;
  }
}
</style>
