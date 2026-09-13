import { http } from './http'

/** 对应后端 CatalogService.ProductView */
export interface Product {
  id: number
  merchantId: number
  categoryId: number
  name: string
  description: string | null
  marketPricePerKg: number
  merchantPricePerKg: number
  availableGrams: number
}

export const catalogApi = {
  /** 公开商品列表，可选按分类筛选 */
  list: (categoryId?: number) => http.get<Product[]>('/catalog/products', { query: { categoryId } }),

  detail: (productId: number) => http.get<Product>(`/catalog/products/${productId}`),

  /** 商家侧：当前商家的商品与可用库存 */
  listMine: () => http.get<Product[]>('/merchant/catalog/products'),

  createProduct: (body: {
    categoryId: number
    name: string
    description?: string
    marketPricePerKg: number
    merchantPricePerKg: number
  }) => http.post<{ id: number }>('/merchant/catalog/products', { body }),

  publishProduct: (productId: number) => http.put<void>(`/merchant/catalog/products/${productId}/publish`),

  createWarehouse: (body: { deliveryZoneId: number; name: string; code: string; address: string }) =>
    http.post<{ id: number }>('/merchant/catalog/warehouses', { body }),

  allowWarehouseCategory: (body: { warehouseId: number; categoryId: number }) =>
    http.post<void>('/merchant/catalog/warehouse-categories', { body }),

  createWarehouseRule: (body: { warehouseId: number; categoryId: number; priority: number }) =>
    http.post<void>('/merchant/catalog/warehouse-rules', { body }),

  createBatch: (body: {
    productId: number
    warehouseId: number
    batchNo: string
    availableGrams: number
    expiresOn?: string
  }  ) => http.post<{ id: number }>('/merchant/catalog/batches', { body })
  }
