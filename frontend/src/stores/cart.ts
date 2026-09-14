import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

const STORAGE_KEY = 'freshmart.cart'

export interface CartLine {
  productId: number
  name: string
  merchantId: number
  merchantPricePerKg: number
  weightGrams: number
}

function load(): CartLine[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as CartLine[]) : []
  } catch {
    return []
  }
}

/** 按克计价商品的本地购物车；提交下单时再交由后端校验价格与库存 */
export const useCartStore = defineStore('cart', () => {
  const lines = ref<CartLine[]>(load())

  watch(lines, (value) => localStorage.setItem(STORAGE_KEY, JSON.stringify(value)), { deep: true })

  const count = computed(() => lines.value.length)
  const totalAmount = computed(() =>
    lines.value.reduce((sum, line) => sum + (line.merchantPricePerKg * line.weightGrams) / 1000, 0)
  )
  const empty = computed(() => lines.value.length === 0)

  function add(line: CartLine) {
    const existing = lines.value.find((item) => item.productId === line.productId)
    if (existing) existing.weightGrams += line.weightGrams
    else lines.value.push({ ...line })
  }

  function setWeight(productId: number, weightGrams: number) {
    const line = lines.value.find((item) => item.productId === productId)
    if (line) line.weightGrams = Math.max(50, weightGrams)
  }

  function remove(productId: number) {
    lines.value = lines.value.filter((item) => item.productId !== productId)
  }

  function clear() {
    lines.value = []
  }

  return { lines, count, totalAmount, empty, add, setWeight, remove, clear }
})
