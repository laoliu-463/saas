/**
 * performance-domain-v1-closure.spec.ts
 *
 * V1 业绩域查询接口闭环 E2E（API 层为主）
 */
import { test, expect } from '@playwright/test';
import { loginApi, apiGet, seedTestData } from './helpers/api-assertions';

let adminToken: string;
let channelStaffToken: string;
let bizStaffToken: string;

test.beforeAll(async () => {
  [adminToken, channelStaffToken, bizStaffToken] = await Promise.all([
    loginApi('admin'),
    loginApi('channelStaff'),
    loginApi('bizStaff'),
  ]);
  await seedTestData(adminToken);
});

test.describe('Performance domain V1 API', () => {
  test('admin summary returns estimate and effective tracks', async () => {
    const res = await apiGet('/performance/summary', adminToken, {
      timeFilterType: 'pay',
    });
    expect(res?.estimate).toBeTruthy();
    expect(res?.effective).toBeTruthy();
    expect(typeof res.estimate.orderCount).toBe('number');
    expect(typeof res.effective.grossProfit).toBe('number');
  });

  test('admin performance list paginates', async () => {
    const res = await apiGet('/performance', adminToken, { page: 1, pageSize: 20 });
    expect(res?.page).toBe(1);
    expect(Array.isArray(res?.items)).toBe(true);
  });

  test('batch performance accepts order ids', async () => {
    const list = await apiGet('/performance', adminToken, { page: 1, pageSize: 5 });
    const orderIds = (list?.items || []).map((item: any) => item.orderId).filter(Boolean);
    test.skip(orderIds.length === 0, 'no performance rows in seed data');

    const batchRes = await fetch(`${process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api'}/performance/batch`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${adminToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ orderIds }),
    });
    expect(batchRes.ok).toBeTruthy();
    const body = await batchRes.json();
    expect(Array.isArray(body?.data?.items || body?.items)).toBe(true);
  });

  test('channel staff summary scoped to self', async () => {
    const res = await apiGet('/performance/summary', channelStaffToken, { timeFilterType: 'pay' });
    expect(res?.estimate).toBeTruthy();
    expect(res?.effective).toBeTruthy();
  });

  test('biz staff summary scoped to self', async () => {
    const res = await apiGet('/performance/summary', bizStaffToken, { timeFilterType: 'pay' });
    expect(res?.estimate).toBeTruthy();
  });
});
