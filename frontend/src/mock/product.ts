export const mockProductList = Array.from({length: 12}).map((_, i) => ({
    id: `uuid-${i}`,
    name: `网红零食大礼包 ${i+1}（限时优惠）`,
    productId: `123456789${i}`,
    shop: '零食官方旗舰店',
    price: (59.9 + i).toFixed(2),
    commissionRate: 0.20,
    serviceFeeRate: 0.15,
    imageUrl: 'https://picsum.photos/300/200?random=' + i,
    sampleRequired: true,
    status: i % 2 === 0 ? 'approved' : 'pending'
}));

export const mockActivityList = [
    { id: 'uuid-a', name: '618 大促专属活动', productCount: 120, startTime: '2026-06-01' },
    { id: 'uuid-b', name: '双11 预热报名', productCount: 300, startTime: '2026-10-20' },
];
