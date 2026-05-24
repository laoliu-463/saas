import { describe, expect, it, vi } from 'vitest';

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn()
  }
}));

import request from '../utils/request';
import { getDouyinAuthorizeUrl } from './douyin';

describe('douyin oauth api', () => {
  it('calls authorize-url endpoint with appId', async () => {
    const requestGet = vi.mocked(request.get);
    requestGet.mockResolvedValueOnce({
      data: {
        authorizeUrl: 'https://op.jinritemai.com/oauth2/authorize?state=s1',
        state: 's1',
        redirectUri: 'http://localhost:8081/api/douyin/oauth/callback'
      }
    });

    const result = await getDouyinAuthorizeUrl('app-1');

    expect(requestGet).toHaveBeenCalledWith('/douyin/oauth/authorize-url', {
      params: { appId: 'app-1' },
      timeout: 120_000
    });
    expect(result.authorizeUrl).toContain('oauth2/authorize');
    expect(result.redirectUri).toBe('http://localhost:8081/api/douyin/oauth/callback');
  });
});
