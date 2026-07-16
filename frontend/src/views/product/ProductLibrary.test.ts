import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'

import ProductLibrary from './ProductLibrary.vue'
import { DEFAULT_PRODUCT_FILTERS } from './product-filters'
import { getProductLibraryCategories, getProducts } from '../../api/product'
import { ROLE_CODES } from '../../constants/rbac'
import { PRODUCT_LIBRARY_ROW_HEIGHT } from './product-library-layout'

const messageMock = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}))

const authState = vi.hoisted(() => ({
  roleCodes: ['channel_leader'] as string[],
  isAdmin: false
}))

vi.mock('../../api/product', () => ({
  getProducts: vi.fn(),
  getProductLibraryCategories: vi.fn()
}))

vi.mock('../../api/activityProduct', () => ({
  convertActivityProductLink: vi.fn()
}))

vi.mock('../../stores/auth', () => ({
  useAuthStore: () => authState
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

function mountLibrary(options: { attachTo?: HTMLElement } = {}) {
  return mount(ProductLibrary, {
    attachTo: options.attachTo,
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
          name: 'ProductSelectionCard',
          props: ['card', 'canCopyBrief'],
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
  let attachedRoots: HTMLElement[] = []

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getProducts).mockReset()
    vi.mocked(getProductLibraryCategories).mockReset()
    routeState.path = '/product'
    routeState.query = {}
    vi.mocked(getProductLibraryCategories).mockResolvedValue({ data: [] } as any)
    authState.roleCodes = [ROLE_CODES.CHANNEL_LEADER]
    authState.isAdmin = false
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

  it('allows recruiting staff to generate an attributable promotion link', async () => {
    authState.roleCodes = [ROLE_CODES.BIZ_STAFF]
    vi.mocked(getProducts).mockResolvedValue({
      data: { records: buildRows(1, 1), total: 1, hasMore: false, nextCursor: null }
    } as any)

    const wrapper = mountLibrary()
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ProductSelectionCard' }).props('canCopyBrief')).toBe(true)
  })

  afterEach(() => {
    attachedRoots.forEach((root) => root.remove())
    attachedRoots = []
    vi.unstubAllGlobals()
  })

  function createLayoutScrollHarness(options: { clientWidth?: number; clientHeight?: number } = {}) {
    const clientWidth = options.clientWidth ?? 1280
    const clientHeight = options.clientHeight ?? 900
    const scrollRoot = document.createElement('div')
    scrollRoot.className = 'n-layout-scroll-container'
    scrollRoot.style.overflowY = 'auto'
    Object.defineProperty(scrollRoot, 'clientHeight', {
      configurable: true,
      value: clientHeight
    })
    Object.defineProperty(scrollRoot, 'clientWidth', {
      configurable: true,
      value: clientWidth
    })
    Object.defineProperty(scrollRoot, 'scrollHeight', {
      configurable: true,
      value: 20000
    })
    Object.defineProperty(scrollRoot, 'scrollTop', {
      configurable: true,
      writable: true,
      value: 0
    })
    scrollRoot.getBoundingClientRect = () => ({
      top: 0,
      left: 0,
      bottom: clientHeight,
      right: clientWidth,
      width: clientWidth,
      height: clientHeight,
      x: 0,
      y: 0,
      toJSON: () => ({})
    } as DOMRect)

    const mountPoint = document.createElement('div')
    scrollRoot.appendChild(mountPoint)
    document.body.appendChild(scrollRoot)
    attachedRoots.push(scrollRoot)
    return { scrollRoot, mountPoint }
  }

  async function waitForScheduledViewportUpdate() {
    await new Promise((resolve) => window.setTimeout(resolve, 20))
    await flushPromises()
  }

  it('loads product batches, appends more products, and resets when filters change', async () => {
    vi.mocked(getProducts)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(1, 100),
          total: 0,
          page: 1,
          size: 100,
          hasMore: true,
          nextCursor: 'cursor-1'
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(101, 50),
          total: 0,
          page: 2,
          size: 100,
          hasMore: false,
          nextCursor: null
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(200, 1),
          total: 0,
          page: 1,
          size: 100,
          hasMore: false,
          nextCursor: null
        }
      } as any)

    const wrapper = mountLibrary()
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[0][0]).toMatchObject({
      limit: 100
    })
    expect(vi.mocked(getProducts).mock.calls[0][0].page).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[0][0].size).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[0][0].cursor).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[0][0].sortBy).toBeUndefined()
    expect(wrapper.find('[data-testid="product-library-sort"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(100)
    expect(wrapper.text()).toContain('已加载 100 件')
    expect(wrapper.text()).not.toContain('/ 100')

    await wrapper.get('[data-testid="product-library-load-more"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[1][0]).toMatchObject({
      limit: 100,
      cursor: 'cursor-1'
    })
    expect(vi.mocked(getProducts).mock.calls[1][0].page).toBeUndefined()
    expect(vi.mocked(getProducts).mock.calls[1][0].size).toBeUndefined()
    expect(wrapper.text()).toContain('已加载 150 件')
    expect(wrapper.find('[data-testid="product-grid-virtual-window"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="product-card"]').length).toBeLessThan(150)

    await wrapper.get('[data-testid="emit-filter"]').trigger('click')
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[2][0]).toMatchObject({
      page: 1,
      size: 100,
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
          records: buildRows(1, 100),
          total: 0,
          page: 1,
          size: 100,
          hasMore: true,
          nextCursor: 'cursor-1'
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(101, 100),
          total: 0,
          page: 2,
          size: 100,
          hasMore: true,
          nextCursor: 'cursor-2'
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(201, 50),
          total: 0,
          page: 3,
          size: 100,
          hasMore: false,
          nextCursor: null
        }
      } as any)

    const wrapper = mountLibrary()
    await flushPromises()

    expect(wrapper.find('[data-testid="product-library-scroll-sentinel"]').exists()).toBe(true)
    expect(observeMock).toHaveBeenCalled()
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(100)

    lastIntersectionCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver
    )
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[1][0]).toMatchObject({
      limit: 100,
      cursor: 'cursor-1'
    })
    expect(wrapper.text()).toContain('已加载 200 件')
    expect(wrapper.find('[data-testid="product-grid-virtual-window"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="product-card"]').length).toBeLessThan(200)

    lastIntersectionCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver
    )
    await flushPromises()

    expect(vi.mocked(getProducts).mock.calls[2][0]).toMatchObject({
      limit: 100,
      cursor: 'cursor-2'
    })
    expect(wrapper.text()).toContain('已加载 250 件')
    expect(wrapper.findAll('[data-testid="product-card"]').length).toBeLessThan(250)
    expect(wrapper.text()).toContain('已全部加载')
  })

  it('pauses automatic paging after a load-more failure while keeping manual retry available', async () => {
    vi.mocked(getProducts)
      .mockResolvedValueOnce({
        data: {
          records: buildRows(1, 100),
          total: 0,
          page: 1,
          size: 100,
          hasMore: true,
          nextCursor: 'retry-cursor'
        }
      } as any)
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce({
        data: {
          records: buildRows(101, 100),
          total: 0,
          page: 2,
          size: 100,
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
      limit: 100,
      cursor: 'retry-cursor'
    })
    expect(wrapper.text()).toContain('已加载 200 件')
    expect(wrapper.findAll('[data-testid="product-card"]').length).toBeLessThan(200)
    expect(wrapper.text()).toContain('已全部加载')
  })

  it('keeps four virtual columns after measuring a narrower desktop product grid', async () => {
    vi.mocked(getProducts).mockResolvedValueOnce({
      data: {
        records: buildRows(1, 150),
        total: 150,
        page: 1,
        size: 150,
        hasMore: false,
        nextCursor: null
      }
    } as any)

    const { scrollRoot, mountPoint } = createLayoutScrollHarness({ clientWidth: 1280 })
    const wrapper = mountLibrary({ attachTo: mountPoint })
    await flushPromises()

    const grid = wrapper.get('[data-testid="product-grid"]').element as HTMLElement
    grid.getBoundingClientRect = () => ({
      top: 0,
      left: 0,
      bottom: 20000,
      right: 1054,
      width: 1054,
      height: 20000,
      x: 0,
      y: 0,
      toJSON: () => ({})
    } as DOMRect)

    scrollRoot.dispatchEvent(new Event('scroll'))
    await waitForScheduledViewportUpdate()

    const virtualWindow = wrapper.get('[data-testid="product-grid-virtual-window"]').element as HTMLElement
    expect(virtualWindow.style.gridTemplateColumns).toBe('repeat(4, minmax(0, 1fr))')
  })

  it('updates the virtual window when the layout scroll container scrolls', async () => {
    vi.mocked(getProducts).mockResolvedValueOnce({
      data: {
        records: buildRows(1, 200),
        total: 200,
        page: 1,
        size: 200,
        hasMore: false,
        nextCursor: null
      }
    } as any)

    const { scrollRoot, mountPoint } = createLayoutScrollHarness()
    const wrapper = mountLibrary({ attachTo: mountPoint })
    await flushPromises()

    const grid = wrapper.get('[data-testid="product-grid"]').element as HTMLElement
    grid.getBoundingClientRect = () => ({
      top: -scrollRoot.scrollTop,
      left: 0,
      bottom: 20000 - scrollRoot.scrollTop,
      right: 1280,
      width: 1280,
      height: 20000,
      x: 0,
      y: -scrollRoot.scrollTop,
      toJSON: () => ({})
    } as DOMRect)

    await waitForScheduledViewportUpdate()
    expect(wrapper.findAll('[data-testid="product-card"]').map((card) => card.text())).toContain('product-1')

    scrollRoot.scrollTop = 25 * PRODUCT_LIBRARY_ROW_HEIGHT
    scrollRoot.dispatchEvent(new Event('scroll'))
    await waitForScheduledViewportUpdate()

    const visibleProductIds = wrapper.findAll('[data-testid="product-card"]').map((card) => card.text())
    expect(visibleProductIds).not.toContain('product-1')
    expect(visibleProductIds.some((id) => Number(id.replace('product-', '')) > 80)).toBe(true)
  })
})
