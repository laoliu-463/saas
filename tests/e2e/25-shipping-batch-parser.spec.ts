import { test, expect } from '@playwright/test';
import { parseBatchShipRows } from '../../frontend/src/utils/shippingBatch';

test('batch shipping parser keeps shipperCode separate from trackingNo', () => {
  const result = parseBatchShipRows([
    ['寄样单号', '物流公司', '物流单号'],
    ['SR20260521001', 'SF', 'SF1234567890']
  ]);

  expect(result.errors).toEqual([]);
  expect(result.items).toEqual([
    {
      requestNo: 'SR20260521001',
      shipperCode: 'SF',
      trackingNo: 'SF1234567890'
    }
  ]);
});
