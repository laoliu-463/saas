import { describe, expect, it } from 'vitest';
import { resolveExclusiveStatusView } from './exclusive-status';

const now = new Date('2026-05-21T12:00:00');

describe('resolveExclusiveStatusView', () => {
  it('marks active exclusive status as expiring soon within 7 days', () => {
    expect(resolveExclusiveStatusView({ status: 1, expireAt: '2026-05-27 12:00:00' }, now)).toEqual({
      type: 'warning',
      label: '即将失效'
    });
  });

  it('keeps active exclusive status green when expiry is beyond 7 days', () => {
    expect(resolveExclusiveStatusView({ status: 1, expireAt: '2026-05-30 12:00:00' }, now)).toEqual({
      type: 'success',
      label: '生效中'
    });
  });

  it('marks inactive or expired rows as expired', () => {
    expect(resolveExclusiveStatusView({ status: 0, expireAt: '2026-05-30 12:00:00' }, now)).toEqual({
      type: 'default',
      label: '已过期'
    });
    expect(resolveExclusiveStatusView({ status: 1, expireAt: '2026-05-20 12:00:00' }, now)).toEqual({
      type: 'default',
      label: '已过期'
    });
  });
});
