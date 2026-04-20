export const mockSampleList = Array.from({length: 5}).map((_, i) => ({
    id: `SMP${20260420000 + i}`,
    product: `网红零食大礼包 ${i}`,
    talent: `吃货小分队 ${i}`,
    status: 'pending_audit'
}));
