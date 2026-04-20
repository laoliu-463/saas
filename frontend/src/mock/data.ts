export const mockOrderList = Array.from({length: 50}).map((_, i) => ({
    orderId: `ORD${20260420000 + i}`,
    amount: (Math.random() * 500 + 50).toFixed(2),
    status: i % 3 === 0 ? 'completed' : 'pending'
}));
