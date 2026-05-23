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
  // #region agent log
  fetch('http://127.0.0.1:7649/ingest/87969fc6-601f-41be-a111-33366a0047db',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'02e7cc'},body:JSON.stringify({sessionId:'02e7cc',location:'sample-user-filter-options.ts:loadSampleRecruiterOptions',message:'fetch recruiters start',data:{keyword:normalizeKeyword(keyword)},timestamp:Date.now(),hypothesisId:'H1'})}).catch(()=>{});
  // #endregion
  try {
    const res = await getUserMasterRecruiters({
      keyword: normalizeKeyword(keyword),
      limit: 50
    });
    // #region agent log
    fetch('http://127.0.0.1:7649/ingest/87969fc6-601f-41be-a111-33366a0047db',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'02e7cc'},body:JSON.stringify({sessionId:'02e7cc',location:'sample-user-filter-options.ts:loadSampleRecruiterOptions',message:'fetch recruiters success',data:{code:(res as any)?.code,count:Array.isArray((res as any)?.data)?(res as any).data.length:null},timestamp:Date.now(),hypothesisId:'H1',runId:'post-fix'})}).catch(()=>{});
    // #endregion
    return mapUserOptions(res);
  } catch (error: any) {
    // #region agent log
    fetch('http://127.0.0.1:7649/ingest/87969fc6-601f-41be-a111-33366a0047db',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'02e7cc'},body:JSON.stringify({sessionId:'02e7cc',location:'sample-user-filter-options.ts:loadSampleRecruiterOptions',message:'fetch recruiters failed',data:{code:error?.code,msg:error?.msg},timestamp:Date.now(),hypothesisId:'H1'})}).catch(()=>{});
    // #endregion
    throw error;
  }
};
