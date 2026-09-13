<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useCartStore } from '../../stores/cart'
import { paymentApi, tradeApi, type PaymentView, type TradeView } from '../../api/trade'
import { errorMessage } from '../../api/http'

const cart = useCartStore()

/** 下单必填：配送区域与地址快照；商家与仓库由后端按商品自动推导 */
const deliveryZoneId = ref('')
const contactName = ref('')
const phone = ref('')
const detail = ref('')
const pointsToRedeem = ref('')

const submitting = ref(false)
const trade = ref<TradeView | null>(null)
const payment = ref<PaymentView | null>(null)

/** 积分抵扣上限：单笔最高抵扣商品金额 3%，1000 积分抵 1 元，不抵配送费 */
const pointsLimit = computed(() => Math.floor(cart.totalAmount * 0.03 * 1000))

const canSubmit = computed(
  () =>
    !cart.empty &&
    Boolean(deliveryZoneId.value) &&
    Boolean(contactName.value.trim()) &&
    Boolean(phone.value.trim()) &&
    !submitting.value
)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    trade.value = await tradeApi.create({
      deliveryZoneId: Number(deliveryZoneId.value),
      addressSnapshot: {
        contactName: contactName.value.trim(),
        phone: phone.value.trim(),
        detail: detail.value.trim()
      },
      lines: cart.lines.map((line) => ({ productId: line.productId, weightGrams: line.weightGrams })),
      pointsToRedeem: pointsToRedeem.value ? Number(pointsToRedeem.value) : 0
    })
    cart.clear()
    ElMessage.success('交易单已创建，请在 15 分钟内完成支付')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function loadPaymentCode() {
  if (!trade.value) return
  try {
    payment.value = await paymentApi.prepay(trade.value.tradeNo)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function payWithBalance() {
  if (!trade.value) return
  try {
    trade.value = await paymentApi.confirmBalance(trade.value.tradeNo)
    payment.value = null
    ElMessage.success('余额支付成功')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}
</script>

<template>
  <section class="checkout">
    <header class="page-head">
      <h2>结算下单</h2>
      <span class="sub">创建 15 分钟库存预占的交易单，商家与仓库由后端按商品自动拆分</span>
    </header>

    <el-alert v-if="trade" type="success" :closable="false" show-icon class="checkout__alert">
      <template #title>
        交易单 {{ trade.tradeNo }} 已创建，应付 ¥{{ trade.payableAmount }}
      </template>
      <div class="checkout__actions">
        <el-button type="primary" @click="loadPaymentCode">查看收款码</el-button>
        <el-button @click="payWithBalance">余额支付</el-button>
        <router-link to="/app/orders"><el-button text type="primary">查看订单</el-button></router-link>
      </div>
    </el-alert>

    <el-card v-if="payment" class="checkout__qr" shadow="never">
      <img :src="payment.codeUrl || '/public/payment-qr'" alt="个人微信收款码" class="checkout__qr-image" />
      <div class="checkout__qr-meta">
        <p>应付金额：<strong>¥{{ payment.amount }}</strong></p>
        <p>转账备注：<span class="checkout__remark">{{ payment.remarkText }}</span></p>
        <p class="checkout__hint">请务必在备注中填写交易单号，管理员核验到账后订单才会继续流转。</p>
      </div>
    </el-card>

    <div v-else class="checkout__grid">
      <el-card shadow="never" class="checkout__panel">
        <template #header>订单明细</template>
        <el-empty v-if="cart.empty" description="购物车为空" />
        <template v-else>
          <el-table :data="cart.lines" size="small">
            <el-table-column prop="name" label="商品" min-width="140" />
            <el-table-column label="重量" width="90">
              <template #default="{ row }">{{ row.weightGrams }} g</template>
            </el-table-column>
            <el-table-column label="小计" width="100">
              <template #default="{ row }">
                ¥{{ ((row.merchantPricePerKg * row.weightGrams) / 1000).toFixed(2) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="checkout__total">
            <span>商品金额</span>
            <strong>¥{{ cart.totalAmount.toFixed(2) }}</strong>
          </div>
        </template>
      </el-card>

      <el-card shadow="never" class="checkout__panel">
        <template #header>收货与优惠</template>
        <el-form label-position="top">
          <el-form-item label="配送区域 ID" required>
            <el-input v-model="deliveryZoneId" placeholder="deliveryZoneId" />
          </el-form-item>
          <el-form-item label="联系人" required>
            <el-input v-model="contactName" placeholder="收货人姓名" />
          </el-form-item>
          <el-form-item label="联系电话" required>
            <el-input v-model="phone" placeholder="手机号" />
          </el-form-item>
          <el-form-item label="详细地址">
            <el-input v-model="detail" type="textarea" :rows="2" placeholder="楼栋 / 门牌" />
          </el-form-item>
          <el-form-item label="使用积分（可选）">
            <el-input v-model="pointsToRedeem" type="number" :min="0" placeholder="0" />
            <span class="checkout__hint">本单最多可用 {{ pointsLimit }} 积分，不抵配送费</span>
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" class="checkout__submit" @click="submit">
          提交订单
        </el-button>
      </el-card>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.checkout {
  @include page-shell;

  &__alert {
    margin-bottom: 16px;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }

  &__grid {
    display: grid;
    grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
    gap: 16px;
  }

  &__panel {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__total {
    display: flex;
    align-items: baseline;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 14px;
    color: $text-secondary;

    strong {
      font-size: 18px;
      color: $text-primary;
    }
  }

  &__submit {
    width: 100%;
  }

  &__hint {
    display: block;
    margin-top: 6px;
    font-size: 12px;
    color: $text-muted;
    line-height: 1.6;
  }

  &__qr {
    border-color: $border;
    border-radius: $radius-lg;
  }

  &__qr-image {
    width: 220px;
    border: 1px solid $border;
    border-radius: $radius-md;
  }

  &__qr-meta {
    margin-top: 14px;
    color: $text-secondary;
    line-height: 1.9;

    strong {
      color: $text-primary;
    }
  }

  &__remark {
    font-family: Consolas, monospace;
    color: $mint-deep;
  }
}

@media (max-width: 960px) {
  .checkout__grid {
    grid-template-columns: 1fr;
  }
}
</style>
