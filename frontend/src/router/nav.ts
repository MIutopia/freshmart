import type { ConsoleKey } from '../stores/auth'

export interface NavItem {
  path: string
  label: string
}

export interface NavGroup {
  title: string
  items: NavItem[]
}

/** 四端侧边菜单；页面顺序即业务推进顺序 */
export const CONSOLE_NAV: Record<ConsoleKey, NavGroup[]> = {
  app: [
    {
      title: '选购',
      items: [
        { path: '/app/merchants', label: '商家列表' },
        { path: '/app/products', label: '商品列表' },
        { path: '/app/cart', label: '购物车' },
        { path: '/app/checkout', label: '结算下单' }
      ]
    },
    {
      title: '我的',
      items: [
        { path: '/app/orders', label: '我的订单' },
        { path: '/app/refunds', label: '售后退款' },
        { path: '/app/inbox', label: '站内消息' },
        { path: '/app/ai', label: 'AI 导购' },
        { path: '/app/profile', label: '账号与偏好' }
      ]
    }
  ],
  merchant: [
    {
      title: '经营',
      items: [
        { path: '/merchant/dashboard', label: '经营看板' },
        { path: '/merchant/orders', label: '订单处理' },
        { path: '/merchant/settlements', label: '佣金结算' }
      ]
    },
    {
      title: '商品与库存',
      items: [
        { path: '/merchant/warehouses', label: '仓库与分类规则' },
        { path: '/merchant/products', label: '商品管理' },
        { path: '/merchant/batches', label: '批次入库' }
      ]
    },
    {
      title: '其他',
      items: [
        { path: '/merchant/marketing', label: '营销活动' },
        { path: '/merchant/application', label: '入驻资料' }
      ]
    }
  ],
  delivery: [
    {
      title: '配送',
      items: [
        { path: '/delivery/tasks', label: '配送任务' },
        { path: '/delivery/performance', label: '我的绩效' }
      ]
    }
  ],
  admin: [
    {
      title: '总览',
      items: [{ path: '/admin/dashboard', label: '平台概览' }]
    },
    {
      title: '交易与资金',
      items: [
        { path: '/admin/payments', label: '支付核验与对账' },
        { path: '/admin/refunds', label: '售后审核' },
        { path: '/admin/commissions', label: '佣金与结算' }
      ]
    },
    {
      title: '商家与区域',
      items: [
        { path: '/admin/merchants', label: '商家审核' },
        { path: '/admin/delivery-zones', label: '配送区域' },
        { path: '/admin/dispatch', label: '配送派单' },
        { path: '/admin/categories', label: '商品分类' }
      ]
    },
    {
      title: '运营',
      items: [
        { path: '/admin/platform-rules', label: '平台规则' },
        { path: '/admin/marketing', label: '批次促销排期' },
        { path: '/admin/holiday-cards', label: '节气卡片任务' },
        { path: '/admin/inbox', label: '站内消息' }
      ]
    }
  ]
}

export const CONSOLE_HOME: Record<ConsoleKey, string> = {
  app: '/app/merchants',
  merchant: '/merchant/dashboard',
  delivery: '/delivery/tasks',
  admin: '/admin/dashboard'
}
