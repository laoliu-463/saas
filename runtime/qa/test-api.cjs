async function getAuthToken(apiContext, apiBase, username = 'admin', password = 'admin123') {
  const res = await apiContext.post(`${apiBase}/auth/login`, {
    data: { username, password }
  });
  const body = await res.json().catch(() => ({}));
  const token = body?.data?.token || body?.data?.accessToken;
  if (!res.ok() || !token) {
    throw new Error(`QA API 登录失败: ${username} (${res.status()})`);
  }
  return token;
}

async function postTestAction(apiContext, apiBase, token, path, data) {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  const res = await apiContext.post(`${apiBase}${normalized}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data
  });
  const body = await res.json().catch(() => ({}));
  return {
    ok: res.ok(),
    status: res.status(),
    body: body?.data || body
  };
}

module.exports = {
  getAuthToken,
  postTestAction
};
