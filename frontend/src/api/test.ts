import request from '../utils/request'

export function seedTestData() {
  return request.post('/test/seed')
}

export function resetTestData() {
  return request.post('/test/reset')
}

export function syncTestOrders() {
  return request.post('/test/orders/sync')
}

export function generateAttributedOrder() {
  return request.post('/test/orders/generate-attributed')
}

export function generateNoPickSourceOrder() {
  return request.post('/test/orders/generate-no-pick-source')
}

export function generateMissingMappingOrder() {
  return request.post('/test/orders/generate-missing-mapping')
}

export function testShipSample(sampleRequestId: number | string) {
  return request.post(`/test/logistics/ship/${sampleRequestId}`)
}

export function testSignSample(sampleRequestId: number | string) {
  return request.post(`/test/logistics/sign/${sampleRequestId}`)
}
