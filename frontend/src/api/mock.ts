import request from '../utils/request'

export function seedMockData() {
  return request.post('/mock/seed')
}

export function resetMockData() {
  return request.post('/mock/reset')
}

export function syncMockOrders() {
  return request.post('/mock/orders/sync')
}

export function generateAttributedOrder() {
  return request.post('/mock/orders/generate-attributed')
}

export function generateNoPickSourceOrder() {
  return request.post('/mock/orders/generate-no-pick-source')
}

export function generateMissingMappingOrder() {
  return request.post('/mock/orders/generate-missing-mapping')
}

export function mockShipSample(sampleRequestId: number | string) {
  return request.post(`/mock/logistics/ship/${sampleRequestId}`)
}

export function mockSignSample(sampleRequestId: number | string) {
  return request.post(`/mock/logistics/sign/${sampleRequestId}`)
}
