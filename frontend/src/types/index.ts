export interface UserInfo {
    username: string;
    realName?: string;
    roleCodes: string[];
    dataScope?: string;
}

// 基础分页响应
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

// 用户模块
export interface UserItem {
  id: string;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  status: string; // 'ACTIVE' | 'INACTIVE'
  roleIds: string[];
  roles?: RoleItem[];
  createTime: string;
}

// 角色模块
export interface RoleItem {
  id: string;
  roleName: string;
  roleCode: string;
  description?: string;
  status: string;
}

// 商品与活动模块
export interface ProductItem {
  id: string;
  productName: string;
  status: string;
  price: number;
  createTime: string;
}

export interface ActivityItem {
  id: string;
  activityName: string;
  activityType: string;
  status: string;
  productCount: number;
  createTime: string;
}

// 达人模块
export interface TalentItem {
  id: string;
  talentId?: string;
  talentName: string;
  nickname?: string;
  avatarUrl?: string;
  fansCount: number;
  creditScore: number;
  mainCategory: string;
  region?: string;
  status: string;
  createTime: string;
}

// 寄样台模块
export interface SampleItem {
  id: string;
  requestNo?: string;
  talentId: string;
  talentName?: string;
  talentNickname?: string;
  talentFansCount?: number;
  talentCreditScore?: number;
  talentMainCategory?: string;
  talentAvatarUrl?: string;
  productId: string;
  productName?: string;
  quantity: number;
  channelUserId?: string;
  channelUserName?: string;
  colonelUserId?: string;
  colonelUserName?: string;
  status: string;
  remark?: string;
  trackingNo?: string;
  rejectReason?: string;
  closeReason?: string;
  applyReason?: string;
  eligibilityCheck?: {
    passed?: boolean;
    failedRules?: string[];
    reasons?: string[];
  };
  requirementSnapshot?: {
    min30DaySales?: number;
    minLevel?: string;
    actual30DaySales?: number;
    actualLevel?: string;
    rawStandard?: Record<string, any>;
  };
  createTime: string;
  updateTime?: string;
  completeTime?: string;
}

// 数据大盘与订单模块
export interface OrderItem {
  id: string;
  productName: string;
  talentName?: string;
  amount: number;
  commission: number;
  status: string;
  expressCompany?: string;
  expressNo?: string;
  createTime: string;
}

export interface MetricsData {
  totalOrders: number;
  totalAmount: number;
  serviceFee: number;
  commission: number;
  serviceFeeIncome?: number;
  techServiceFee?: number;
  talentCommission?: number;
  bizCommission?: number;
  channelCommission?: number;
  grossProfit?: number;
}

// 订单解密
export interface DecryptResultItem {
  orderId: string;
  isVirtualTel: boolean;
  phone?: string;           // 非虚拟号时返回
  phoneNoA?: string;        // 虚拟号 A（达人号，脱敏）
  phoneNoB?: string;        // 虚拟号 B（快递员号，脱敏）
  expireTime?: number;      // 虚拟号过期时间（秒级时间戳）
}

// 转链结果
export interface PromotionLinkResult {
  shortId: string;
  shortLink: string;
  promoteLink: string;
  uuidSeed: string;
}
