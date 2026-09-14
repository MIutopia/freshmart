<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { catalogApi, type Product } from '../../api/catalog'
import { errorMessage } from '../../api/http'
import { useCartStore } from '../../stores/cart'

const cart = useCartStore()
const products = ref<Product[]>([])
const loading = ref(false)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    products.value = await catalogApi.list()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

const visibleProducts = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return products.value
  return products.value.filter((product) => product.name.toLowerCase().includes(key))
})

function addToCart(product: Product) {
  cart.add({
    productId: product.id,
    name: product.name,
    merchantId: product.merchantId,
    merchantPricePerKg: product.merchantPricePerKg,
    weightGrams: 500
  })
  ElMessage.success(`已加入购物车：${product.name} 500g`)
}

function initial(name: string) {
  return name.slice(0, 1)
}

onMounted(load)
</script>

<template>
  <section class="products">
    <header class="page-head">
      <h2>商品列表</h2>
      <span class="sub">按千克计价，加入购物车后按克调整；仅展示已上架商品</span>
    </header>

    <div class="products__toolbar">
      <el-input v-model="keyword" placeholder="搜索商品名称" clearable :prefix-icon="Search" class="products__search" />
      <el-button :loading="loading" @click="load">刷新</el-button>
      <router-link class="products__cart-link" to="/app/cart">
        <el-badge :value="cart.count" :hidden="cart.count === 0">
          <el-button>购物车</el-button>
        </el-badge>
      </router-link>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated class="products__skeleton" />

    <el-empty v-else-if="visibleProducts.length === 0" description="暂无已上架商品" />

    <div v-else class="products__grid">
      <article v-for="product in visibleProducts" :key="product.id" class="product-card">
        <div class="product-card__top">
          <span class="product-card__avatar">{{ initial(product.name) }}</span>
          <div class="product-card__identity">
            <h3 class="product-card__name">{{ product.name }}</h3>
            <p class="product-card__desc">{{ product.description || '暂无描述' }}</p>
          </div>
        </div>

        <div class="product-card__price">
          <span class="product-card__amount">¥{{ product.merchantPricePerKg }}<em>/kg</em></span>
          <span class="product-card__market">市场价 ¥{{ product.marketPricePerKg }}/kg</span>
        </div>

        <footer class="product-card__footer">
          <el-tag size="small" effect="plain" type="info">可售 {{ product.availableGrams }} g</el-tag>
          <el-button type="primary" size="small" round :disabled="product.availableGrams <= 0" @click="addToCart(product)">
            加入购物车
          </el-button>
        </footer>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.products {
  @include page-shell;

  &__toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    padding: 16px 18px;
    margin-bottom: 20px;
    @include panel;
  }

  &__search {
    width: 260px;
  }

  &__cart-link {
    margin-left: auto;
  }

  &__skeleton {
    padding: 20px;
    @include panel;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 18px;
  }
}

.product-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  @include panel;
  @include card-hover;

  &__top {
    display: flex;
    gap: 12px;
  }

  &__avatar {
    flex: 0 0 42px;
    width: 42px;
    height: 42px;
    display: grid;
    place-items: center;
    font-size: 16px;
    font-weight: 650;
    color: $mint-deep;
    background: $mint-soft;
    border-radius: $radius-md;
  }

  &__identity {
    min-width: 0;
  }

  &__name {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__desc {
    margin: 6px 0 0;
    font-size: 12.5px;
    line-height: 1.6;
    color: $text-muted;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__price {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }

  &__amount {
    font-size: 19px;
    font-weight: 700;
    color: $text-primary;

    em {
      font-size: 12px;
      font-style: normal;
      font-weight: 400;
      color: $text-muted;
    }
  }

  &__market {
    font-size: 12px;
    color: $text-muted;
    text-decoration: line-through;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-top: auto;
    padding-top: 12px;
    border-top: 1px solid $border;
  }
}

@media (max-width: 1280px) {
  .products__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .products {
    &__grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    &__search {
      width: 100%;
    }

    &__cart-link {
      margin-left: 0;
    }
  }
}

@media (max-width: 640px) {
  .products__grid {
    grid-template-columns: 1fr;
  }
}
</style>
