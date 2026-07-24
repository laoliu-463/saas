import { test, expect } from '@playwright/test';
import { loginWithCredentials } from './helpers/auth';

test('activityName appears in colonel activities products API', async ({ request }) => {
  const baseUrl = process.env.E2E_BACKEND_URL || 'http://localhost:8081';

  const auth = await loginWithCredentials(
    { username: 'admin', password: process.env.E2E_ADMIN_PASSWORD || 'admin123' },
    { backendUrl: baseUrl }
  );
  const token = auth.token || auth.accessToken;
  expect(token, 'No token returned').toBeTruthy();

  // Call products API
  const apiResp = await request.get(
    `${baseUrl}/api/colonel/activities/3929905/products?count=1`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  expect(apiResp.ok(), `API failed: ${apiResp.status()}`).toBeTruthy();
  const apiData = await apiResp.json();
  const items = apiData.data?.items || apiData.items || [];
  expect(items.length, 'No items returned').toBeGreaterThan(0);
  const firstItem = items[0];
  console.log('Item keys:', Object.keys(firstItem));
  expect(Object.keys(firstItem), 'activityName not in item keys').toContain('activityName');
  expect(firstItem.activityName).toBeTruthy();
  console.log('activityName:', firstItem.activityName);
});
