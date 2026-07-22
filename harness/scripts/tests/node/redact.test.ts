import { describe, expect, it } from "vitest";

import { redactEvidenceText } from "../../lib/node/core/redact.js";

describe("证据文本脱敏", () => {
  it("按最长值优先清理重叠密钥并忽略空值", () => {
    const result = redactEvidenceText(
      "长值=abc123；短值=abc；无关值=xyz。",
      ["", "abc", "abc123"],
    );

    expect(result).toBe("长值=[REDACTED]；短值=[REDACTED]；无关值=xyz。");
    expect(result).not.toContain("abc");
  });

  it("窄范围清理 key=value、JSON 和 Authorization 凭据", () => {
    const source = [
      "token=token-value",
      "password: 'password-value'",
      '{"secret":"secret-value"}',
      "Authorization: Bearer authorization-value",
      "tokenCount=3 ordinary=keep",
    ].join("\n");

    const result = redactEvidenceText(source);

    expect(result).not.toMatch(/token-value|password-value|secret-value|authorization-value/u);
    expect(result).toContain("token=[REDACTED]");
    expect(result).toContain("password: [REDACTED]");
    expect(result).toContain('"secret":"[REDACTED]"');
    expect(result).toContain("Authorization: [REDACTED]");
    expect(result).toContain("tokenCount=3 ordinary=keep");
  });

  it("完整清理 JSON 凭据中转义的双引号和反斜杠", () => {
    const source = String.raw`{"password":"abc\"def\\ghi","ordinary":"keep"}`;

    const result = redactEvidenceText(source);

    expect(result).toBe('{"password":"[REDACTED]","ordinary":"keep"}');
    expect(result).not.toMatch(/abc|def|ghi/u);
  });

  it("完整清理 key=value 凭据中转义的双引号和反斜杠", () => {
    const source = String.raw`password="abc\"def\\ghi" ordinary=keep`;

    const result = redactEvidenceText(source);

    expect(result).toBe("password=[REDACTED] ordinary=keep");
    expect(result).not.toMatch(/abc|def|ghi/u);
  });

  it("显式密钥先于未加引号的通用 password 规则清理", () => {
    const secret = String.raw`head"quote-tail-991\slash-tail-991`;
    const escapedSecret = JSON.stringify(secret).slice(1, -1);

    const result = redactEvidenceText(`password=${secret}`, [secret]);

    expect(result).toBe("password=[REDACTED]");
    expect(result).not.toContain(secret);
    expect(result).not.toContain(escapedSecret);
    expect(result).not.toContain("quote-tail-991");
    expect(result).not.toContain("slash-tail-991");
  });

  it("没有密钥特征时保持文本不变", () => {
    const source = "Node.js 20 验证通过，普通参数保持不变。";

    expect(redactEvidenceText(source)).toBe(source);
  });
});
