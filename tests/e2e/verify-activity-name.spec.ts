import { test, expect } from '@playwright/test';

test('activityName appears in colonel activities products API', async ({ request }) => {
  const baseUrl = process.env.E2E_BACKEND_URL || 'http://localhost:8081';

  // Login
  const loginResp = await request.post(`${baseUrl}/api/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  });
  expect(loginResp.ok(), `Login failed: ${loginResp.status()}`).toBeTruthy();
  const loginData = await loginResp.json();
  const token = loginData.data?.token;
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
