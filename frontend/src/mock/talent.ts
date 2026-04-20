export const mockTalentList = Array.from({length: 10}).map((_, i) => ({
    id: `t-${i}`,
    name: `吃货小分队 ${i}`,
    category: '美食、生活',
    followers: (10000 * (i + 1)) + 500,
    status: 'public'
}));
