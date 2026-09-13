<script setup lang="ts">
import { computed, ref } from 'vue'
import { Clock, Location, Refresh, Search, Van } from '@element-plus/icons-vue'

/** 商家营业状态 */
type MerchantStatus = 'OPEN' | 'RESTING' | 'CLOSED'

/** 商家卡片视图模型 */
interface MerchantCard {
  id: number
  name: string
  /** 头像占位文字，取商家名首字 */
  initial: string
  categories: string[]
  rating: number
  monthlySales: number
  productCount: number
  /** 起送金额，单位元 */
  minOrderAmount: number
  /** 配送费，单位元 */
  deliveryFee: number
  deliveryZones: string[]
  /** 预计送达时长，单位分钟 */
  deliveryMinutes: number
  status: MerchantStatus
  tags: string[]
}

/** 列表排序方式 */
type SortKey = 'DEFAULT' | 'RATING' | 'MIN_ORDER'

const props = defineProps<{
  /** 外部注入商家列表；缺省时使用内置演示数据 */
  merchants?: MerchantCard[]
  /** 列表加载态 */
  loading?: boolean
}>()

const STATUS_LABEL: Record<MerchantStatus, string> = {
  OPEN: '营业中',
  RESTING: '休息中',
  CLOSED: '已打烊'
}

const STATUS_TAG_TYPE: Record<MerchantStatus, 'success' | 'warning' | 'info'> = {
  OPEN: 'success',
  RESTING: 'warning',
  CLOSED: 'info'
}

const SORT_OPTIONS: Array<{ label: string; value: SortKey }> = [
  { label: '综合排序', value: 'DEFAULT' },
  { label: '评分优先', value: 'RATING' },
  { label: '起送价从低到高', value: 'MIN_ORDER' }
]

/**
 * 演示数据。
 * 后端当前只有公开商品接口，尚无商家档案查询接口；
 * 待 `GET /api/catalog/merchants` 就绪后，改为通过 props 注入即可移除本段。
 */
const DEMO_MERCHANTS: MerchantCard[] = [
  {
    id: 1,
    name: '鲜达果蔬·城东店',
    initial: '鲜',
    categories: ['叶菜', '瓜果', '菌菇'],
    rating: 4.9,
    monthlySales: 1286,
    productCount: 86,
    minOrderAmount: 20,
    deliveryFee: 6,
    deliveryZones: ['城东片区', '高新片区'],
    deliveryMinutes: 30,
    status: 'OPEN',
    tags: ['当日采摘', '满 59 免运费']
  },
  {
    id: 2,
    name: '青禾生鲜集市',
    initial: '青',
    categories: ['根茎', '瓜果'],
    rating: 4.7,
    monthlySales: 934,
    productCount: 64,
    minOrderAmount: 15,
    deliveryFee: 5,
    deliveryZones: ['城西片区'],
    deliveryMinutes: 40,
    status: 'OPEN',
    tags: ['产地直供']
  },
  {
    id: 3,
    name: '山里人土货铺',
    initial: '山',
    categories: ['菌菇', '禽蛋'],
    rating: 4.8,
    monthlySales: 512,
    productCount: 38,
    minOrderAmount: 30,
    deliveryFee: 8,
    deliveryZones: ['城东片区', '城南片区'],
    deliveryMinutes: 45,
    status: 'RESTING',
    tags: ['散养土鸡蛋']
  },
  {
    id: 4,
    name: '每日鲜果行',
    initial: '每',
    categories: ['瓜果', '进口果'],
    rating: 4.6,
    monthlySales: 1587,
    productCount: 112,
    minOrderAmount: 25,
    deliveryFee: 6,
    deliveryZones: ['城东片区', '高新片区', '城南片区'],
    deliveryMinutes: 35,
    status: 'OPEN',
    tags: ['坏果包赔', '冷链直达']
  },
  {
    id: 5,
    name: '邻家小菜园',
    initial: '邻',
    categories: ['叶菜', '根茎'],
    rating: 4.5,
    monthlySales: 407,
    productCount: 29,
    minOrderAmount: 12,
    deliveryFee: 4,
    deliveryZones: ['城西片区', '城南片区'],
    deliveryMinutes: 25,
    status: 'OPEN',
    tags: ['社区自提']
  },
  {
    id: 6,
    name: '海味坊水产',
    initial: '海',
    categories: ['水产', '禽蛋'],
    rating: 4.4,
    monthlySales: 268,
    productCount: 41,
    minOrderAmount: 40,
    deliveryFee: 10,
    deliveryZones: ['城南片区'],
    deliveryMinutes: 55,
    status: 'CLOSED',
    tags: ['活鲜现杀']
  }
]

const keyword = ref('')
const activeCategory = ref('全部')
const onlyOpen = ref(false)
const sortKey = ref<SortKey>('DEFAULT')

const merchantSource = computed(() => props.merchants ?? DEMO_MERCHANTS)

/** 分类选项由商家数据动态汇总 */
const categoryOptions = computed(() => [
  '全部',
  ...new Set(merchantSource.value.flatMap((merchant) => merchant.categories))
])

const visibleMerchants = computed(() => {
  const searchKey = keyword.value.trim().toLowerCase()

  const filtered = merchantSource.value.filter((merchant) => {
    if (onlyOpen.value && merchant.status !== 'OPEN') return false
    if (activeCategory.value !== '全部' && !merchant.categories.includes(activeCategory.value)) return false
    if (!searchKey) return true

    return (
      merchant.name.toLowerCase().includes(searchKey) ||
      merchant.categories.some((category) => category.toLowerCase().includes(searchKey)) ||
      merchant.deliveryZones.some((zone) => zone.toLowerCase().includes(searchKey))
    )
  })

  switch (sortKey.value) {
    case 'RATING':
      return [...filtered].sort((a, b) => b.rating - a.rating)
    case 'MIN_ORDER':
      return [...filtered].sort((a, b) => a.minOrderAmount - b.minOrderAmount)
    default:
      return filtered
  }
})

function resetFilters() {
  keyword.value = ''
  activeCategory.value = '全部'
  onlyOpen.value = false
  sortKey.value = 'DEFAULT'
}

function formatSales(value: number) {
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value)
}
</script>

<template>
  <section class="merchant-list">
    <header class="merchant-list__head">
      <h2 class="merchant-list__title">附近生鲜商家</h2>
      <p class="merchant-list__subtitle">
        当前区域可配送 <em>{{ visibleMerchants.length }}</em> 家商家
      </p>
    </header>

    <div class="merchant-list__filters">
      <el-input
        v-model="keyword"
        class="merchant-list__search"
        placeholder="搜索商家、分类或配送区域"
        clearable
        :prefix-icon="Search"
      />

      <el-select v-model="activeCategory" class="merchant-list__select" placeholder="全部分类">
        <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
      </el-select>

      <el-select v-model="sortKey" class="merchant-list__select" placeholder="排序方式">
        <el-option v-for="option in SORT_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>

      <el-switch v-model="onlyOpen" class="merchant-list__switch" active-text="仅看营业中" />

      <el-button class="merchant-list__reset" :icon="Refresh" plain @click="resetFilters">重置</el-button>
    </div>

    <el-skeleton v-if="loading" class="merchant-list__skeleton" :rows="6" animated />

    <el-empty v-else-if="visibleMerchants.length === 0" description="没有符合条件的商家" />

    <div v-else class="merchant-list__grid">
      <article v-for="merchant in visibleMerchants" :key="merchant.id" class="merchant-card">
        <div class="merchant-card__top">
          <span class="merchant-card__avatar">{{ merchant.initial }}</span>

          <div class="merchant-card__identity">
            <h3 class="merchant-card__name">{{ merchant.name }}</h3>
            <div class="merchant-card__metrics">
              <span class="merchant-card__rating">{{ merchant.rating.toFixed(1) }}</span>
              <span class="merchant-card__divider">|</span>
              <span>月售 {{ formatSales(merchant.monthlySales) }}</span>
              <span class="merchant-card__divider">|</span>
              <span>{{ merchant.productCount }} 件商品</span>
            </div>
          </div>

          <el-tag class="merchant-card__status" :type="STATUS_TAG_TYPE[merchant.status]" size="small" effect="light">
            {{ STATUS_LABEL[merchant.status] }}
          </el-tag>
        </div>

        <div class="merchant-card__categories">
          <el-tag v-for="category in merchant.categories" :key="category" size="small" effect="plain" type="info">
            {{ category }}
          </el-tag>
        </div>

        <ul class="merchant-card__delivery">
          <li>
            <el-icon><Van /></el-icon>
            <span>起送 ¥{{ merchant.minOrderAmount }} · 配送费 ¥{{ merchant.deliveryFee }}</span>
          </li>
          <li>
            <el-icon><Clock /></el-icon>
            <span>预计 {{ merchant.deliveryMinutes }} 分钟送达</span>
          </li>
          <li>
            <el-icon><Location /></el-icon>
            <span>{{ merchant.deliveryZones.join(' / ') }}</span>
          </li>
        </ul>

        <footer class="merchant-card__footer">
          <div class="merchant-card__tags">
            <span v-for="tag in merchant.tags" :key="tag" class="merchant-card__tag">{{ tag }}</span>
          </div>
          <el-button type="primary" size="small" round :disabled="merchant.status !== 'OPEN'">进入店铺</el-button>
        </footer>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.merchant-list {
  min-height: 100%;

  &__head {
    margin-bottom: 18px;
  }

  &__title {
    margin: 0;
    font-size: 20px;
    font-weight: 650;
    color: $text-primary;
  }

  &__subtitle {
    margin: 8px 0 0;
    font-size: 13px;
    color: $text-secondary;

    em {
      font-style: normal;
      font-weight: 650;
      color: $mint-deep;
    }
  }

  &__filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    padding: 16px 18px;
    margin-bottom: 20px;
    background: $surface;
    border: 1px solid $border;
    border-radius: $radius-lg;
  }

  &__search {
    width: 280px;
  }

  &__select {
    width: 168px;
  }

  &__reset {
    margin-left: auto;
  }

  &__skeleton {
    padding: 20px;
    background: $surface;
    border: 1px solid $border;
    border-radius: 14px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 18px;
  }
}

.merchant-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: $surface;
  border: 1px solid $border;
  border-radius: 14px;
  transition: transform 0.28s cubic-bezier(0.22, 0.61, 0.36, 1),
    box-shadow 0.28s cubic-bezier(0.22, 0.61, 0.36, 1), border-color 0.28s ease;
  will-change: transform;

  &:hover {
    transform: translateY(-3px);
    border-color: rgba($mint, 0.35);
    box-shadow: 0 10px 24px rgba(31, 45, 43, 0.08), 0 2px 6px rgba(31, 45, 43, 0.04);
  }

  &__top {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  &__avatar {
    flex: 0 0 44px;
    width: 44px;
    height: 44px;
    display: grid;
    place-items: center;
    font-size: 17px;
    font-weight: 650;
    color: $mint-deep;
    background: $mint-soft;
    border-radius: 12px;
  }

  &__identity {
    flex: 1;
    min-width: 0;
  }

  &__name {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
    line-height: 1.4;
    color: $text-primary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__metrics {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 5px;
    font-size: 12px;
    color: $text-secondary;
  }

  &__rating {
    font-weight: 650;
    color: $mint-deep;
  }

  &__divider {
    color: $border;
  }

  &__status {
    flex: 0 0 auto;
  }

  &__categories {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__delivery {
    margin: 0;
    padding: 12px 0 0;
    list-style: none;
    border-top: 1px dashed $border;

    li {
      display: flex;
      align-items: center;
      gap: 7px;
      font-size: 12.5px;
      line-height: 1.9;
      color: $text-secondary;
    }

    .el-icon {
      color: $mint;
      font-size: 14px;
    }
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

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-width: 0;
  }

  &__tag {
    padding: 2px 8px;
    font-size: 11.5px;
    color: $mint-deep;
    background: $mint-soft;
    border-radius: 6px;
    white-space: nowrap;
  }
}

@media (max-width: 1280px) {
  .merchant-list__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .merchant-list {
    &__grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    &__search,
    &__select {
      width: 100%;
    }

    &__reset {
      margin-left: 0;
    }
  }
}

@media (max-width: 640px) {
  .merchant-list {
    &__grid {
      grid-template-columns: 1fr;
      gap: 14px;
    }

    &__filters {
      padding: 14px;
      gap: 10px;
    }
  }
}
</style>
