import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'

import ProductLibrary from './ProductLibrary.vue'
import { DEFAULT_PRODUCT_FILTERS } from './product-filters'
import { getProductLibraryCategories, getProducts } from '../../api/product'
import { ROLE_CODES } from '../../constants/rbac'

const messageMock = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}))

vi.mock('../../api/product', () => ({
  getProducts: vi.fn(),
  getProductLibraryCategories: vi.fn()
}))

vi.mock('../../api/activityProduct', () => ({
  convertActivityProductLink: vi.fn()
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => ({
    roleCodes: [ROLE_CODES.CHANNEL_LEADER],
    isAdmin: false
  })
}))

const routeState = reactive({
  path: '/product',
  query: {} as Record<string, string>
})

const replaceMock = vi.fn()
const observeMock = vi.fn()
const disconnectMock = vi.fn()
let lastIntersectionCallback: IntersectionObserverCallback | null = null

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    replace: replaceMock
  })
}))

vi.mock('naive-ui', () => ({
  useMessage: () => messageMock,
  createDiscreteApi: () => ({
    loadingBar: {
      start: vi.fn(),
      finish: vi.fn(),
      error: vi.fn()
    },
    message: messageMock
  })
}))

function buildRows(start: number, count: number) {
  return Array.from({ length: count }, (_, index) => {
    const id = String(start + index)
    return {
      id: `relation-${id}`,
      productId: `product-${id}`,
      title: `测试商品 ${id}`,
      activityId: `activity-${id}`
    }
  })
}

function mountLibrary() {
  return mount(ProductLibrary, {
    global: {
      stubs: {
        PageHeader: {
          template: '<header><slot name="actions" /></header>'
        },
        ProductLibraryFilterPanel: {
          props: ['filters', 'libraryStatus', 'loading', 'categoryOptions'],
          emits: ['update:filters', 'update:libraryStatus', 'search-click', 'reset'],
          setup(props: any, { emit }: any) {
            const applyTag = () => {
              emit('update:filters', {
                ...props.filters,
                productTags: ['主推']
              })
            }
            return {
              applyTag,
              defaults: DEFAULT_PRODUCT_FILTERS()
            }
          },
          template: `
            <section data-testid="product-library-filter-panel">
              <button data-testid="emit-filter" @click="applyTag">筛选</button>
              <button data-testid="emit-search" @click="$emit('search-click')">查询</button>
              <button data-testid="emit-reset" @click="$emit('reset')">重置</button>
            </section>
          `
        },
        ProductSelectionCard: {
          props: ['card'],
          template: '<article data-testid="product-card">{{ card.productId }}</article>'
        },
        ProductDetail: true,
        QuickSampleModal: true,
        ManualCopyDialog: true,
        PageEmpty: true,
        NSpin: {
          template: '<div><slot /></div>'
        },
        NButton: {
          props: ['loading'],
          template: '<button v-bind="$attrs"><slot /></button>'
        },
        NSpace: {
          template: '<div><slot /></div>'
        },
        NSelect: true
      }
    }
  })
}

describe('ProductLibrary infinite scroll', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getProducts).mockReset()
    vi.mocked(getProductLibraryCategories).mockReset()
    routeState.path = '/product'
    routeState.query = {}
    vi.mocked(getProductLibraryCategories).mockResolvedValue({ data: [] } as any)
    lastIntersectionCallback = null
    const intersectionObserverMock = vi.fn((callback: IntersectionObserverCallback) => {
      lastIntersectionCallback = callback
      return {
        observe: observeMock,
        disconnect: disconnectMock,
        unobserve: vi.fn(),
        takeRecords: vi.fn()
      }
    })
    vi.stubGlobal('IntersectionObserver', intersectionObserverMock)
    Object.defineProperty(window, 'IntersectionObserver', {
      configurable: true,
      writable: true,
      value: intersectionObserverMock
    })
  })

  it('loads product batches, appends more products, and resets when filters change', async () => {
    vi.mocked(getProducts)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(1, 500),
          total: 0,
          page: 1,
          size: 500,
          hasMore: true,
          nextCursor: 'cursor-1'
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(501, 250),
          total: 0,
          page: 2,
          size: 500,
          hasMore: false,
          nextCursor: null
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(200, 1),
          total: 0,
          page: 1,
          size: 500,
          hasMore: false,
          nextCursor: null
        }
      } as any)

    const wrapper = mountLibrary()
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[0][0]).toMatchObject({
      limit: 500
    })
    expect(vi.mocked(getProducts).mock.calls[0][0].page).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[0][0].size).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[0][0].cursor).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[0][0].sortBy).toBeUndefined()
    expect(wrapper.find('[data-testid="product-library-sort"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(500)
    expect(wrapper.text()).toContain('已加载 500 件')
    expect(wrapper.text()).not.toContain('/ 500')

    await wrapper.get('[data-testid="product-library-load-more"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[1][0]).toMatchObject({
      limit: 500,
      cursor: 'cursor-1'
    })
    expect(vi.mocked(getProducts).mock.calls[1][0].page).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[1][0].size).toBeUndefined()
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(750)

    await wrapper.get('[data-testid="emit-filter"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[2][0]).toMatchObject({
      page: 1,
      size: 500,
      productTags: '主推'
    })
    expect(vi.mocked(getProducts).mock.calls[2][0].limit).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[2][0].cursor).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[2][0].sortBy).toBeUndefined()
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('已全部加载')
  })

  it('keeps appending product pages when the scroll trigger enters the viewport', async () => {
    vi.mocked(getProducts)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(1, 500),
          total: 0,
          page: 1,
          size: 500,
          hasMore: true,
          nextCursor: 'cursor-1'
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(501, 500),
          total: 0,
          page: 2,
          size: 500,
          hasMore: true,
          nextCursor: 'cursor-2'
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(1001, 250),
          total: 0,
          page: 3,
          size: 500,
          hasMore: false,
          nextCursor: null
        }
      } as any)

    const wrapper = mountLibrary()
    await flushPromises()

    expect(wrapper.find('[data-testid="product-library-scroll-sentinel"]').exists()).toBe(true)
    expect(observeMock).toHaveBeenCalled()
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(500)

    lastIntersectionCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver
    )
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[1][0]).toMatchObject({
      limit: 500,
      cursor: 'cursor-1'
    })
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(1000)

    lastIntersectionCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver
    )
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[2][0]).toMatchObject({
      limit: 500,
      cursor: 'cursor-2'
    })
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(1250)
    expect(wrapper.text()).toContain('已全部加载')
  })

  it('pauses automatic paging after a load-more failure while keeping manual retry available', async () => {
    vi.mocked(getProducts)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(1, 500),
          total: 0,
          page: 1,
          size: 500,
          hasMore: true,
          nextCursor: 'retry-cursor'
        }
      } as any)
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce({
        data: {
          records: buildRows(501, 500),
          total: 0,
          page: 2,
          size: 500,
          hasMore: false,
          nextCursor: null
        }
      } as any)

    const wrapper = mountLibrary()
    await flushPromises()

    lastIntersectionCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver
    )
    await flushPromises()

    expect(vi.mocked(getProducts)).toHaveBeenCalledTimes(2)

    lastIntersectionCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver
    )
    await flushPromises()

    expect(vi.mocked(getProducts)).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-testid="product-library-load-more"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[2][0]).toMatchObject({
      limit: 500,
      cursor: 'retry-cursor'
    })
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(1000)
    expect(wrapper.text()).toContain('已全部加载')
  })
})
