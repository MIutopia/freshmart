import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import { useAuthStore, type ConsoleKey } from '../stores/auth'
import { CONSOLE_HOME } from './nav'
import { configureHttp } from '../api/http'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },

  {
    path: '/app',
    component: AppLayout,
    meta: { console: 'app' },
    children: [
      { path: '', redirect: '/app/merchants' },
      { path: 'merchants', name: 'app-merchants', component: () => import('../views/app/MerchantsView.vue') },
      { path: 'products', name: 'app-products', component: () => import('../views/app/ProductsView.vue') },
      { path: 'products/:productId', name: 'app-product-detail', component: () => import('../views/app/ProductDetailView.vue') },
      { path: 'cart', name: 'app-cart', component: () => import('../views/app/CartView.vue') },
      { path: 'checkout', name: 'app-checkout', component: () => import('../views/app/CheckoutView.vue') },
      { path: 'orders', name: 'app-orders', component: () => import('../views/app/OrdersView.vue') },
      { path: 'orders/:orderId', name: 'app-order-detail', component: () => import('../views/app/OrderDetailView.vue') },
      { path: 'refunds', name: 'app-refunds', component: () => import('../views/app/RefundsView.vue') },
      { path: 'inbox', name: 'app-inbox', component: () => import('../views/app/InboxView.vue') },
      { path: 'ai', name: 'app-ai', component: () => import('../views/app/AiAssistantView.vue') },
      { path: 'profile', name: 'app-profile', component: () => import('../views/app/ProfileView.vue') }
    ]
  },

  {
    path: '/merchant',
    component: AppLayout,
    meta: { console: 'merchant' },
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      { path: 'dashboard', name: 'merchant-dashboard', component: () => import('../views/merchant/DashboardView.vue') },
      { path: 'orders', name: 'merchant-orders', component: () => import('../views/merchant/OrdersView.vue') },
      { path: 'settlements', name: 'merchant-settlements', component: () => import('../views/merchant/SettlementsView.vue') },
      { path: 'warehouses', name: 'merchant-warehouses', component: () => import('../views/merchant/WarehousesView.vue') },
      { path: 'products', name: 'merchant-products', component: () => import('../views/merchant/ProductsView.vue') },
      { path: 'batches', name: 'merchant-batches', component: () => import('../views/merchant/BatchesView.vue') },
      { path: 'marketing', name: 'merchant-marketing', component: () => import('../views/merchant/MarketingView.vue') },
      { path: 'application', name: 'merchant-application', component: () => import('../views/merchant/ApplicationView.vue') }
    ]
  },

  {
    path: '/delivery',
    component: AppLayout,
    meta: { console: 'delivery' },
    children: [
      { path: '', redirect: '/delivery/tasks' },
      { path: 'tasks', name: 'delivery-tasks', component: () => import('../views/delivery/TasksView.vue') },
      { path: 'performance', name: 'delivery-performance', component: () => import('../views/delivery/PerformanceView.vue') }
    ]
  },

  {
    path: '/admin',
    component: AppLayout,
    meta: { console: 'admin' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('../views/admin/DashboardView.vue') },
      { path: 'payments', name: 'admin-payments', component: () => import('../views/admin/PaymentsView.vue') },
      { path: 'refunds', name: 'admin-refunds', component: () => import('../views/admin/RefundsView.vue') },
      { path: 'commissions', name: 'admin-commissions', component: () => import('../views/admin/CommissionsView.vue') },
      { path: 'merchants', name: 'admin-merchants', component: () => import('../views/admin/MerchantsView.vue') },
      { path: 'delivery-zones', name: 'admin-delivery-zones', component: () => import('../views/admin/DeliveryZonesView.vue') },
      { path: 'categories', name: 'admin-categories', component: () => import('../views/admin/CategoriesView.vue') },
      { path: 'platform-rules', name: 'admin-platform-rules', component: () => import('../views/admin/PlatformRulesView.vue') },
      { path: 'marketing', name: 'admin-marketing', component: () => import('../views/admin/MarketingView.vue') },
      { path: 'holiday-cards', name: 'admin-holiday-cards', component: () => import('../views/admin/HolidayCardsView.vue') },
      { path: 'inbox', name: 'admin-inbox', component: () => import('../views/admin/InboxView.vue') }
    ]
  },

  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 统一请求层与登录态绑定；401 时清空并回到登录页
  configureHttp({
    getToken: () => auth.token || null,
    onUnauthorized: () => {
      auth.clear()
      void router.replace({ name: 'login' })
    }
  })

  await auth.restore()

  if (to.meta.public) {
    if (auth.isAuthenticated && to.name === 'login') return CONSOLE_HOME[auth.primaryConsole()]
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const consoleKey = to.matched.map((record) => record.meta.console).find(Boolean) as ConsoleKey | undefined
  if (consoleKey && !auth.canAccess(consoleKey)) {
    return CONSOLE_HOME[auth.primaryConsole()]
  }

  return true
})

export default router
