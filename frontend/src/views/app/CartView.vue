<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { useCartStore } from '../../stores/cart'

const cart = useCartStore()

function lineAmount(pricePerKg: number, grams: number) {
  return ((pricePerKg * grams) / 1000).toFixed(2)
}

function clearCart() {
  cart.clear()
  ElMessage.success('购物车已清空')
}
</script>

<template>
  <section class="cart">
    <header class="page-head">
      <h2>购物车</h2>
      <span class="sub">本地暂存，提交下单时由后端按商家与仓库重新校验价格与库存</span>
    </header>

    <el-card shadow="never" class="cart__panel">
      <template #header>
        <div class="cart__header">
          <span>共 {{ cart.count }} 种商品</span>
          <div class="cart__header-actions">
            <el-button v-if="!cart.empty" text type="danger" @click="clearCart">清空</el-button>
            <router-link to="/app/products"><el-button text type="primary">继续选购</el-button></router-link>
          </div>
        </div>
      </template>

      <el-empty v-if="cart.empty" description="购物车为空，先去挑选商品" />

      <template v-else>
        <el-table :data="cart.lines" size="small">
          <el-table-column prop="name" label="商品" min-width="160" />
          <el-table-column label="商家" width="80">
            <template #default="{ row }">#{{ row.merchantId }}</template>
          </el-table-column>
          <el-table-column label="单价" width="110">
            <template #default="{ row }">¥{{ row.merchantPricePerKg }}/kg</template>
          </el-table-column>
          <el-table-column label="重量（g）" width="160">
            <template #default="{ row }">
              <el-input-number
                :model-value="row.weightGrams"
                :min="50"
                :step="50"
                size="small"
                controls-position="right"
                @change="(value: number | undefined) => cart.setWeight(row.productId, value ?? 50)"
              />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="110">
            <template #default="{ row }">¥{{ lineAmount(row.merchantPricePerKg, row.weightGrams) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button text type="danger" size="small" @click="cart.remove(row.productId)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="cart__footer">
          <span class="cart__total-label">商品金额合计</span>
          <strong class="cart__total-amount">¥{{ cart.totalAmount.toFixed(2) }}</strong>
          <router-link to="/app/checkout">
            <el-button type="primary">去结算</el-button>
          </router-link>
        </div>
      </template>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.cart {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: $text-secondary;
    font-size: 13px;
  }

  &__header-actions {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 14px;
    margin-top: 18px;
  }

  &__total-label {
    color: $text-secondary;
  }

  &__total-amount {
    font-size: 20px;
    color: $text-primary;
  }
}
</style>
