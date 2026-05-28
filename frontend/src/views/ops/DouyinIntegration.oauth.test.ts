import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import DouyinIntegration from './DouyinIntegration.vue';
import { getDouyinAuthorizeUrl, getDouyinTokenStatus } from '../../api/douyin';

const source = readFileSync(resolve(__dirname, 'DouyinIntegration.vue'), 'utf8');
const messageApi = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  info: vi.fn()
}));

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>();
  return {
    ...actual,
    useMessage: () => messageApi
  };
});

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} })
}));

vi.mock('../../api/activityProduct', () => ({
  getActivityProducts: vi.fn()
}));

vi.mock('../../api/data', () => ({
  getMetrics: vi.fn()
}));

vi.mock('../../api/order', () => ({
  getOrders: vi.fn(),
  syncOrders: vi.fn()
}));

vi.mock('../../api/douyin', () => ({
  createDouyinToken: vi.fn(),
  getDouyinAuthorizeUrl: vi.fn(),
  getDouyinActivityProductList: vi.fn(),
  getDouyinActivityTest: vi.fn(),
  getDouyinInstitutionInfo: vi.fn(),
  getDouyinOrderSettlements: vi.fn(),
  getDouyinProductActivities: vi.fn(),
  getDouyinTokenStatus: vi.fn(),
  postDouyinRawProbe: vi.fn(),
  refreshDouyinToken: vi.fn()
}));

const naiveStubs = {
  PageHeader: { template: '<section><slot name="actions" /><slot /></section>' },
  NAlert: { template: '<div><slot /></div>' },
  NButton: { template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>' },
  NDescriptions: { template: '<dl><slot /></dl>' },
  NDescriptionsItem: { template: '<div><slot /></div>' },
  NEmpty: { template: '<div />' },
  NInput: { template: '<input />' },
  NSelect: { template: '<div />' },
  NTabPane: { template: '<section><slot /></section>' },
  NTabs: { template: '<div><slot /></div>' },
  NTag: { template: '<span><slot /></span>' }
};

describe('DouyinIntegration oauth entry', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', `${window.location.origin}/`);
    vi.mocked(getDouyinTokenStatus).mockResolvedValue({
      appId: 'default-app',
      hasAccessToken: false,
      maskedAccessToken: '',
      hasRefreshToken: false,
      maskedRefreshToken: '',
      tokenExpireAtEpochSeconds: 0,
      tokenExpiringSoon: false,
      reauthorizeRequired: true
    });
    vi.mocked(getDouyinAuthorizeUrl).mockResolvedValue({
      authorizeUrl: 'https://op.jinritemai.com/oauth2/authorize?state=s1',
      state: 's1',
      redirectUri: 'http://localhost:8081/api/douyin/oauth/callback',
      powerManageUrl: 'https://buyin.jinritemai.com/dashboard/institution/power-manage'
    });
  });

  it('renders one-click douyin authorization entry and handles callback query', () => {
    expect(source).toContain('去抖店授权');
    expect(source).toContain('官方授权管理');
    expect(source).toContain('@click="startDouyinOAuth"');
    expect(source).toContain('@click="openDouyinPowerManage"');
    expect(source).toContain('getDouyinAuthorizeUrl');
    expect(source).toContain('window.location.href = result.authorizeUrl');
    expect(source).toContain('result.powerManageUrl');
    expect(source).toContain("route.query.oauth");
    expect(source).toContain("oauthResult === 'success'");
  });

  it('requests authorize-url and redirects browser to douyin oauth endpoint', async () => {
    const wrapper = mount(DouyinIntegration, {
      global: {
        stubs: naiveStubs
      }
    });

    const authorizeButton = wrapper.findAll('button').find((button) => button.text().includes('去抖店授权'));
    expect(authorizeButton).toBeTruthy();

    await authorizeButton!.trigger('click');
    await Promise.resolve();

    expect(getDouyinAuthorizeUrl).toHaveBeenCalledWith(undefined);
    expect(window.location.href).toBe('https://op.jinritemai.com/oauth2/authorize?state=s1');
  });

  it('opens official buyin power management page from token tools', async () => {
    const wrapper = mount(DouyinIntegration, {
      global: {
        stubs: naiveStubs
      }
    });

    const powerManageButton = wrapper.findAll('button').find((button) => button.text().includes('官方授权管理'));
    expect(powerManageButton).toBeTruthy();

    await powerManageButton!.trigger('click');
    await Promise.resolve();

    expect(getDouyinAuthorizeUrl).toHaveBeenCalledWith(undefined);
    expect(window.location.href).toBe('https://buyin.jinritemai.com/dashboard/institution/power-manage');
  });
});
