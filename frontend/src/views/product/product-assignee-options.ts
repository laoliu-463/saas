import { getUserMasterRecruiters } from '../../api/sys';

const buildUserOption = (user: any) => {
  const realName = String(user?.realName || '').trim();
  const username = String(user?.username || '').trim();
  const label = realName && username ? `${realName} (${username})` : (realName || username || String(user?.id || '未命名用户'));
  return {
    label,
    value: String(user?.id || '')
  };
};

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
