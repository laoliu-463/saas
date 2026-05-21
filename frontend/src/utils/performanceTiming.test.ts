import { afterEach, describe, expect, it, vi } from 'vitest';
import { recordFrontendTiming } from './performanceTiming';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('recordFrontendTiming', () => {
  it('logs timing payloads and emits a frontend timing event', () => {
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const events: unknown[] = [];
    const listener = (event: Event) => {
      events.push((event as CustomEvent).detail);
    };
    window.addEventListener('frontend:timing', listener);

    recordFrontendTiming('api', {
      method: 'GET',
      url: '/api/dashboard/summary',
      status: 200,
      durationMs: 42
    });

    window.removeEventListener('frontend:timing', listener);
    expect(infoSpy).toHaveBeenCalledWith('[api timing]', {
      method: 'GET',
      url: '/api/dashboard/summary',
      status: 200,
      durationMs: 42
    });
    expect(events).toEqual([
      {
        kind: 'api',
        failed: false,
        method: 'GET',
        url: '/api/dashboard/summary',
        status: 200,
        durationMs: 42
      }
    ]);
  });

  it('uses warning logs for failed timings', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    recordFrontendTiming('router', { to: '/orders', durationMs: 120 }, { failed: true });

    expect(warnSpy).toHaveBeenCalledWith('[router timing]', { to: '/orders', durationMs: 120 });
  });
});
