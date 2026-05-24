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

export const loadSampleChannelOptions = async (keyword: string) =>
  mapUserOptions(await getUserMasterChannels({
    keyword: normalizeKeyword(keyword),
    limit: 50
  }));

export const loadSampleRecruiterOptions = async (keyword: string) => {
  const res = await getUserMasterRecruiters({
    keyword: normalizeKeyword(keyword),
    limit: 50
  });
  return mapUserOptions(res);
};

export const mapFilterOptionItems = (items: Array<{ label?: string; value?: string }> | undefined) =>
  (items || [])
    .map((item) => ({
      label: String(item?.label || item?.value || ''),
      value: String(item?.value || '')
    }))
    .filter((item) => Boolean(item.value));
