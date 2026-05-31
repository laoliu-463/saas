import { chromium } from '@playwright/test';

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
const page = await context.newPage();
const baseUrl = process.env.E2E_BACKEND_URL || 'http://localhost:8081';

const loginResp = await page.request.post(`${baseUrl}/api/auth/login`, {
  data: { username: 'admin', password: 'admin123' }
});
const loginData = await loginResp.json();
const token = loginData.data ? loginData.data.token : null;
console.log('Login:', loginResp.ok() ? 'OK' : 'FAIL', loginData.msg || loginData.message || '');

if (token) {
  const apiResp = await page.request.get(
    `${baseUrl}/api/colonel/activities/3929905/products?count=1`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  const apiData = await apiResp.json();
  console.log('API status:', apiResp.status());
  console.log('Full response:', JSON.stringify(apiData, null, 2).substring(0, 500));
  if (apiData.items && apiData.items.length > 0) {
    const item = apiData.items[0];
    console.log('Item keys:', Object.keys(item));
    console.log('activityName:', item.activityName);
    console.log('FIX VERIFIED:', Object.keys(item).includes('activityName') ? 'YES' : 'NO');
  } else {
    console.log('No items, total:', apiData.total);
  }
} else {
  console.log('No token, response:', JSON.stringify(loginData).substring(0, 200));
}

await browser.close();
