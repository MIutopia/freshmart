<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '../../api/merchant'
import { errorMessage } from '../../api/http'

const activeTab = ref('promotion')
const submitting = ref(false)

const PROMOTION_TYPES = [
  { label: '满减', value: 'FULL_REDUCTION' },
  { label: '折扣', value: 'DISCOUNT' },
  { label: '会员', value: 'MEMBER' },
  { label: '积分', value: 'POINTS' }
]

const promotion = ref({
  promotionType: 'FULL_REDUCTION',
  name: '',
  rule: '{\n  "thresholdAmount": 59,\n  "discountAmount": 5\n}',
  startsAt: '',
  endsAt: '',
  stackable: true
})

const coupon = ref({
  name: '',
  couponType: 'FULL_REDUCTION',
  thresholdAmount: '',
  discountAmount: '',
  startsAt: '',
  endsAt: '',
  totalQuantity: ''
})

const flashSale = ref({
  productId: '',
  salePricePerKg: '',
  totalGrams: '',
  perUserLimitGrams: '',
  startsAt: '',
  endsAt: ''
})

async function run(action: () => Promise<unknown>, successText: string) {
  submitting.value = true
  try {
    await action()
    ElMessage.success(successText)
    return true
  } catch (error) {
    ElMessage.error(errorMessage(error))
    return false
  } finally {
    submitting.value = false
  }
}

async function submitPromotion() {
  if (!promotion.value.name.trim() || !promotion.value.startsAt || !promotion.value.endsAt) {
    ElMessage.warning('名称与起止时间必填')
    return
  }
  let rule: Record<string, unknown>
  try {
    rule = JSON.parse(promotion.value.rule)
  } catch {
    ElMessage.warning('规则 JSON 格式不正确')
    return
  }
  const ok = await run(
    () =>
      merchantApi.createPromotion({
        promotionType: promotion.value.promotionType,
        name: promotion.value.name.trim(),
        rule,
        startsAt: promotion.value.startsAt,
        endsAt: promotion.value.endsAt,
        stackable: promotion.value.stackable
      }),
    '促销规则已创建'
  )
  if (ok) promotion.value.name = ''
}

async function submitCoupon() {
  if (!coupon.value.name.trim() || !coupon.value.thresholdAmount || !coupon.value.discountAmount || !coupon.value.totalQuantity) {
    ElMessage.warning('名称、门槛、面额、数量与起止时间必填')
    return
  }
  if (!coupon.value.startsAt || !coupon.value.endsAt) {
    ElMessage.warning('起止时间必填')
    return
  }
  const ok = await run(
    () =>
      merchantApi.createCoupon({
        name: coupon.value.name.trim(),
        couponType: coupon.value.couponType,
        thresholdAmount: Number(coupon.value.thresholdAmount),
        discountAmount: Number(coupon.value.discountAmount),
        startsAt: coupon.value.startsAt,
        endsAt: coupon.value.endsAt,
        totalQuantity: Number(coupon.value.totalQuantity)
      }),
    '优惠券已创建'
  )
  if (ok) coupon.value.name = ''
}

async function submitFlashSale() {
  const { productId, salePricePerKg, totalGrams, perUserLimitGrams, startsAt, endsAt } = flashSale.value
  if (!productId || !salePricePerKg || !totalGrams || !perUserLimitGrams || !startsAt || !endsAt) {
    ElMessage.warning('秒杀需填写商品、秒杀价、总克数、限购与起止时间')
    return
  }
  const ok = await run(
    () =>
      merchantApi.createFlashSale({
        productId: Number(productId),
        salePricePerKg: Number(salePricePerKg),
        totalGrams: Number(totalGrams),
        perUserLimitGrams: Number(perUserLimitGrams),
        startsAt,
        endsAt
      }),
    '秒杀活动已创建'
  )
  if (ok) flashSale.value = { productId: '', salePricePerKg: '', totalGrams: '', perUserLimitGrams: '', startsAt: '', endsAt: '' }
}
</script>

<template>
  <section class="marketing">
    <header class="page-head">
      <h2>营销活动</h2>
      <span class="sub">叠加顺序已固定：先批次折扣，再会员与优惠券；秒杀价与普通折扣互斥</span>
    </header>

    <el-card shadow="never" class="marketing__panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="促销规则" name="promotion">
          <el-form label-position="top" class="marketing__form">
            <el-form-item label="促销类型" required>
              <el-select v-model="promotion.promotionType" allow-create filterable>
                <el-option v-for="item in PROMOTION_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="活动名称" required>
              <el-input v-model="promotion.name" placeholder="如 周末满减" />
            </el-form-item>
            <el-form-item label="规则 JSON" required class="marketing__span">
              <el-input v-model="promotion.rule" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="开始时间" required>
              <el-date-picker v-model="promotion.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="promotion.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item label="可叠加">
              <el-switch v-model="promotion.stackable" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="submitting" @click="submitPromotion">创建促销规则</el-button>
        </el-tab-pane>

        <el-tab-pane label="优惠券" name="coupon">
          <el-form label-position="top" class="marketing__form">
            <el-form-item label="券名称" required>
              <el-input v-model="coupon.name" placeholder="如 满 59 减 5" />
            </el-form-item>
            <el-form-item label="券类型" required>
              <el-select v-model="coupon.couponType">
                <el-option label="满减" value="FULL_REDUCTION" />
                <el-option label="折扣" value="DISCOUNT" />
              </el-select>
            </el-form-item>
            <el-form-item label="使用门槛（元）" required>
              <el-input v-model="coupon.thresholdAmount" placeholder="59.00" />
            </el-form-item>
            <el-form-item label="优惠金额（元）" required>
              <el-input v-model="coupon.discountAmount" placeholder="5.00" />
            </el-form-item>
            <el-form-item label="发放总量" required>
              <el-input v-model="coupon.totalQuantity" placeholder="100" />
            </el-form-item>
            <el-form-item label="开始时间" required>
              <el-date-picker v-model="coupon.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="coupon.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="submitting" @click="submitCoupon">创建优惠券</el-button>
        </el-tab-pane>

        <el-tab-pane label="秒杀" name="flash-sale">
          <el-form label-position="top" class="marketing__form">
            <el-form-item label="商品 ID" required>
              <el-input v-model="flashSale.productId" placeholder="productId" />
            </el-form-item>
            <el-form-item label="秒杀价（元/kg）" required>
              <el-input v-model="flashSale.salePricePerKg" placeholder="3.90" />
            </el-form-item>
            <el-form-item label="活动总克数" required>
              <el-input v-model="flashSale.totalGrams" placeholder="20000" />
            </el-form-item>
            <el-form-item label="单用户限购（g）" required>
              <el-input v-model="flashSale.perUserLimitGrams" placeholder="2000" />
            </el-form-item>
            <el-form-item label="开始时间" required>
              <el-date-picker v-model="flashSale.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="flashSale.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="submitting" @click="submitFlashSale">创建秒杀活动</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.marketing {
  @include page-shell;

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__form {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0 16px;
    max-width: 980px;
  }

  &__span {
    grid-column: 1 / -1;
  }
}

@media (max-width: 960px) {
  .marketing__form {
    grid-template-columns: 1fr;
  }
}
</style>
