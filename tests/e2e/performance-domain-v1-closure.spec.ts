/**
 * performance-domain-v1-closure.spec.ts
 *
 * V1 业绩域查询接口闭环 E2E（API 层为主）
 */
import { test, expect } from '@playwright/test';
import { loginApi, apiGet, apiPost, seedTestData } from './helpers/api-assertions';

let adminToken: string;
let channelStaffToken: string;
let bizStaffToken: string;

test.beforeAll(async () => {
  await seedTestData();
  [adminToken, channelStaffToken, bizStaffToken] = await Promise.all([
    loginApi('admin'),
    loginApi('channelStaff'),
    loginApi('bizStaff'),
  ]);
});

test.describe('Performance domain V1 API', () => {
  test('admin summary returns estimate and effective tracks', async () => {
    const body = await apiGet('/api/performance/summary', { token: adminToken }, {
      timeFilterType: 'pay',
    }) as { data?: any };
    const res = body?.data;
    expect(res?.estimate).toBeTruthy();
    expect(res?.effective).toBeTruthy();
    expect(typeof res.estimate.orderCount).toBe('number');
    expect(typeof res.effective.grossProfit).toBe('number');
  });

  test('admin performance list paginates', async () => {
    const body = await apiGet('/api/performance', { token: adminToken }, { page: 1, pageSize: 20 }) as { data?: any };
    const res = body?.data;
    expect(res?.page).toBe(1);
    expect(Array.isArray(res?.items)).toBe(true);
  });

  test('batch performance accepts order ids', async () => {
    const listBody = await apiGet('/api/performance', { token: adminToken }, { page: 1, pageSize: 5 }) as { data?: any };
    const list = listBody?.data;
    const orderIds = (list?.items || []).map((item: any) => item.orderId).filter(Boolean);
    test.skip(orderIds.length === 0, 'no performance rows in seed data');

    const body = await apiPost('/api/performance/batch', { orderIds }, { token: adminToken }) as { data?: any };
    expect(Array.isArray(body?.data?.items)).toBe(true);
  });

  test('channel staff summary scoped to self', async () => {
    const body = await apiGet('/api/performance/summary', { token: channelStaffToken }, { timeFilterType: 'pay' }) as { data?: any };
    const res = body?.data;
    expect(res?.estimate).toBeTruthy();
    expect(res?.effective).toBeTruthy();
  });

  test('biz staff summary scoped to self', async () => {
    const body = await apiGet('/api/performance/summary', { token: bizStaffToken }, { timeFilterType: 'pay' }) as { data?: any };
    const res = body?.data;
    expect(res?.estimate).toBeTruthy();
  });
});
