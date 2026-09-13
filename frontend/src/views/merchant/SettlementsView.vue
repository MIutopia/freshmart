<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { merchantApi, type MerchantSettlement } from '../../api/merchant'
import { errorMessage } from '../../api/http'

const settlements = ref<MerchantSettlement[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    settlements.value = await merchantApi.settlements()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function statusTagType(status: string) {
  if (status === 'SETTLED') return 'success'
  if (status === 'REVERSED') return 'danger'
  return 'warning'
}

onMounted(load)
</script>

<template>
  <section class="settlements">
    <header class="page-head">
      <h2>佣金结算</h2>
      <span class="sub">佣金基数排除平台市场价补贴；退款会反冲已生成佣金</span>
    </header>

    <el-card shadow="never" class="settlements__panel">
      <template #header>
        <div class="settlements__header">
          <span>共 {{ settlements.length }} 条结算记录</span>
          <el-button :icon="Refresh" :loading="loading" text @click="load">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="settlements" size="small">
        <el-table-column prop="id" label="结算单" width="90" />
        <el-table-column prop="orderId" label="子订单" width="90" />
        <el-table-column label="毛收入" width="110">
          <template #default="{ row }">¥{{ row.grossAmount }}</template>
        </el-table-column>
        <el-table-column label="佣金基数" width="110">
          <template #default="{ row }">¥{{ row.commissionBaseAmount }}</template>
        </el-table-column>
        <el-table-column label="平台补贴" width="110">
          <template #default="{ row }">¥{{ row.platformPriceSubsidyAmount }}</template>
        </el-table-column>
        <el-table-column label="佣金" width="110">
          <template #default="{ row }">¥{{ row.commissionAmount }} ({{ row.commissionRate }}%)</template>
        </el-table-column>
        <el-table-column label="净收入" width="110">
          <template #default="{ row }">
            <strong>¥{{ row.netAmount }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结算备注" min-width="150">
          <template #default="{ row }">{{ row.settlementNote || '—' }}</template>
        </el-table-column>
        <template #empty>暂无结算记录</template>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.settlements {
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
