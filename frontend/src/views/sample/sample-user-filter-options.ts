/**
 * 寄样模块用户筛选选项加载器
 *
 * 为寄样列表的渠道和招商筛选下拉框提供异步选项加载功能。
 * 从用户主数据 API 拉取渠道人员和招商人员列表，转换为 Select 组件所需的选项格式。
 */
import { getUserMasterChannels, getUserMasterRecruiters } from '../../api/sys';

/**
 * 将原始用户数据构建为标准选项对象
 *
 * 兼容多种后端返回格式：优先展示「姓名 (用户名)」组合格式，
 * 仅有姓名或用户名时取其一，均无时显示「未命名用户」。
 *
 * @param user - 原始用户数据对象
 * @returns 包含 label（显示名）和 value（用户 ID）的选项对象
 */
const buildUserOption = (user: any) => {
  const realName = String(user?.realName || '').trim();
  const username = String(user?.username || '').trim();
  const label = realName && username ? `${realName} (${username})` : (realName || username || String(user?.id || '未命名用户'));
  return {
    label,
    value: String(user?.id || '')
  };
};

/**
 * 标准化搜索关键词：去除首尾空格，空字符串返回 undefined
 *
 * @param keyword - 原始搜索关键词
 * @returns 非空字符串或 undefined（传给 API 时忽略空关键词）
 */
const normalizeKeyword = (keyword: string) => String(keyword || '').trim() || undefined;

/**
 * 将 API 响应中的用户列表映射为标准选项数组
 *
 * 从响应的 data 字段提取用户列表，过滤掉缺少有效 ID 的条目。
 *
 * @param res - API 响应对象（包含 data 数组）
 * @returns 合法的选项数组
 */
const mapUserOptions = (res: any) => {
  const records = res?.data || [];
  return records
    .map(buildUserOption)
    .filter((item: { label: string; value: string }) => Boolean(item.value));
};

/**
 * 加载寄样渠道筛选选项
 *
 * 从用户主数据 API 获取渠道人员列表，支持关键词搜索和数量限制。
 *
 * @param keyword - 搜索关键词（支持模糊匹配）
 * @returns 渠道人员选项数组（label 为「姓名 (用户名)」格式，value 为用户 ID）
 */
export const loadSampleChannelOptions = async (keyword: string) =>
  mapUserOptions(await getUserMasterChannels({
    keyword: normalizeKeyword(keyword),
    limit: 50
  }));

/**
 * 加载寄样招商筛选选项
 *
 * 从用户主数据 API 获取招商人员列表，支持关键词搜索和数量限制。
 *
 * @param keyword - 搜索关键词（支持模糊匹配）
 * @returns 招商人员选项数组（label 为「姓名 (用户名)」格式，value 为用户 ID）
 */
export const loadSampleRecruiterOptions = async (keyword: string) => {
  const res = await getUserMasterRecruiters({
    keyword: normalizeKeyword(keyword),
    limit: 50
  });
  return mapUserOptions(res);
};

/**
 * 将原始选项数组映射为标准 Select 选项格式
 *
 * 确保每个选项都有有效的 label 和 value，过滤掉无效条目。
 *
 * @param items - 原始选项数组（label/value 可能为 undefined）
 * @returns 标准化的 Select 选项数组
 */
export const mapFilterOptionItems = (items: Array<{ label?: string; value?: string }> | undefined) =>
  (items || [])
    .map((item) => ({
      label: String(item?.label || item?.value || ''),
      value: String(item?.value || '')
    }))
    .filter((item) => Boolean(item.value));
