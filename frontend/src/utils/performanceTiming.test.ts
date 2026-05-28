/**
 * performanceTiming 单元测试
 *
 * 测试 recordFrontendTiming 函数的行为：
 * 1. 成功场景：正确输出 info 日志并派发 CustomEvent
 * 2. 失败场景：使用 warn 级别日志输出
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { recordFrontendTiming } from './performanceTiming';

afterEach(() => {
  vi.restoreAllMocks(); // 每个测试后恢复 console 的原始行为
});

describe('recordFrontendTiming', () => {
  // 验证：成功场景下正确输出日志并派发包含完整信息的 CustomEvent
  it('logs timing payloads and emits a frontend timing event', () => {
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {}); // 捕获 info 日志
    const events: unknown[] = [];
    const listener = (event: Event) => {
      events.push((event as CustomEvent).detail); // 收集派发的事件数据
    };
    window.addEventListener('frontend:timing', listener);

    recordFrontendTiming('api', {
      method: 'GET',
      url: '/api/dashboard/summary',
      status: 200,
      durationMs: 42
    });

    window.removeEventListener('frontend:timing', listener);

    // 验证控制台输出
    expect(infoSpy).toHaveBeenCalledWith('[api timing]', {
      method: 'GET',
      url: '/api/dashboard/summary',
      status: 200,
      durationMs: 42
    });

    // 验证 CustomEvent 包含 kind、failed 标志和所有 payload 数据
    expect(events).toEqual([
      {
        kind: 'api',
        failed: false, // 默认非失败
        method: 'GET',
        url: '/api/dashboard/summary',
        status: 200,
        durationMs: 42
      }
    ]);
  });

  // 验证：失败场景下使用 console.warn 级别输出日志
  it('uses warning logs for failed timings', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {}); // 捕获 warn 日志

    recordFrontendTiming('router', { to: '/orders', durationMs: 120 }, { failed: true });

    expect(warnSpy).toHaveBeenCalledWith('[router timing]', { to: '/orders', durationMs: 120 });
  });
});
