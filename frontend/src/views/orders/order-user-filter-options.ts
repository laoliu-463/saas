import { getUserMasterChannels, getUserMasterRecruiters } from '../../api/sys';

const buildUserOption = (user: any) => {
  const realName = String(user?.realName || '').trim();
  const username = String(user?.username || '').trim();
  const label = realName && username ? `${realName} (${username})` : (realName || username || String(user?.id || '未命名用户'));
  return {
    label,
    value: String(user?.id || '')
  };
};

const normalizeKeyword = (keyword: string) => String(keyword || '').trim() || undefined;

const mapUserOptions = (res: any) => {
  const records = res?.data || [];
  return records
    .map(buildUserOption)
    .filter((item: { label: string; value: string }) => Boolean(item.value));
};

export const loadOrderChannelOptions = async (keyword: string) =>
  mapUserOptions(await getUserMasterChannels({
    keyword: normalizeKeyword(keyword),
    limit: 50
  }));

export const loadOrderRecruiterOptions = async (keyword: string) =>
  mapUserOptions(await getUserMasterRecruiters({
    keyword: normalizeKeyword(keyword),
    limit: 50
  }));
