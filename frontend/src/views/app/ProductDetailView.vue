<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { catalogApi, type Product } from '../../api/catalog'
import { errorMessage } from '../../api/http'
import { useCartStore } from '../../stores/cart'

const route = useRoute()
const cart = useCartStore()

const product = ref<Product | null>(null)
const weightGrams = ref(500)
const loading = ref(false)

const productId = Number(route.params.productId)

async function load() {
  loading.value = true
  try {
    product.value = await catalogApi.detail(productId)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

function addToCart() {
  if (!product.value) return
  cart.add({
    productId: product.value.id,
    name: product.value.name,
    merchantId: product.value.merchantId,
    merchantPricePerKg: product.value.merchantPricePerKg,
    weightGrams: weightGrams.value
  })
  ElMessage.success(`已加入购物车 ${weightGrams.value} g`)
}

onMounted(load)
</script>

<template>
  <section class="product-detail">
    <header class="page-head">
      <h2>商品详情</h2>
      <span class="sub">SKU #{{ productId }}</span>
    </header>

    <el-skeleton v-if="loading" :rows="5" animated class="product-detail__skeleton" />

    <div v-else-if="product" class="product-detail__grid">
      <el-card shadow="never" class="product-detail__panel">
        <template #header>{{ product.name }}</template>
        <p class="product-detail__desc">{{ product.description || '暂无描述' }}</p>
        <el-descriptions :column="2" border size="small" class="product-detail__specs">
          <el-descriptions-item label="商家价">¥{{ product.merchantPricePerKg }}/kg</el-descriptions-item>
          <el-descriptions-item label="市场价">¥{{ product.marketPricePerKg }}/kg</el-descriptions-item>
          <el-descriptions-item label="商家">#{{ product.merchantId }}</el-descriptions-item>
          <el-descriptions-item label="分类">#{{ product.categoryId }}</el-descriptions-item>
          <el-descriptions-item label="可售库存" :span="2">{{ product.availableGrams }} g</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="product-detail__panel">
        <template #header>加入购物车</template>
        <el-form label-position="top">
          <el-form-item label="购买重量（克）">
            <el-input-number v-model="weightGrams" :min="50" :step="50" controls-position="right" />
          </el-form-item>
          <el-form-item label="预计金额">
            <span class="product-detail__amount">
              ¥{{ ((product.merchantPricePerKg * weightGrams) / 1000).toFixed(2) }}
            </span>
          </el-form-item>
        </el-form>
        <el-button type="primary" :disabled="product.availableGrams <= 0" @click="addToCart">加入购物车</el-button>
        <router-link to="/app/cart"><el-button text type="primary">去购物车</el-button></router-link>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.product-detail {
  @include page-shell;

  &__skeleton {
    padding: 20px;
    @include panel;
  }

  &__grid {
    display: grid;
    grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
    gap: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__desc {
    margin: 0 0 16px;
    color: $text-secondary;
    line-height: 1.8;
  }

  &__amount {
    font-size: 18px;
    font-weight: 700;
    color: $text-primary;
  }
}

@media (max-width: 960px) {
  .product-detail__grid {
    grid-template-columns: 1fr;
  }
}
</style>
