/**
 * 商品招商组长选择器选项加载模块
 *
 * 功能：从用户主数据接口加载招商组长列表，构建为 NSelect 选项格式。
 * 使用场景：商品分配招商组长、商品批量指派等下拉选择场景。
 */
import { getUserMasterRecruiters } from '../../api/sys';

/**
 * 将用户记录转换为 NSelect 选项格式
 * - label 格式优先为 "真实姓名 (用户名)"
 * - 仅有姓名时显示姓名，仅有用户名时显示用户名
 * - 都没有时回退到用户 ID 或 "未命名用户"
 * @param user 用户记录对象
 * @returns NSelect 选项 { label, value }
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
 * 加载商品招商组长选项列表
 *
 * 调用用户主数据接口获取招商组长，支持关键字搜索。
 * 返回值去除了 value 为空的无效选项。
 *
 * @param keyword 搜索关键字（会被自动 trim，空值则不传 keyword 参数）
 * @returns 招商组长选项数组，每项包含 label 和 value
 */
export const loadProductAssigneeOptions = async (keyword: string) => {
  const normalizedKeyword = String(keyword || '').trim();
  const res: any = await getUserMasterRecruiters({
    keyword: normalizedKeyword || undefined,
    limit: 50
  });
  const records = res?.data || [];
  return records
    .map(buildUserOption)
    .filter((item: { label: string; value: string }) => Boolean(item.value));
};
