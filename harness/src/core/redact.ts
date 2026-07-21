const REDACTION = "[REDACTED]";
const JSON_CREDENTIAL_PATTERN =
  /(["'](?:token|password|secret|authorization)["']\s*:\s*)("(?:\\[^\r\n]|[^"\\\r\n])*"|'(?:\\[^\r\n]|[^'\\\r\n])*')/giu;
const AUTHORIZATION_PATTERN =
  /(\bauthorization\b\s*[:=]\s*)(?:Bearer\s+)?(?:"(?:\\[^\r\n]|[^"\\\r\n])*"|'(?:\\[^\r\n]|[^'\\\r\n])*'|[^\s,;&|"'\\]+)/giu;
const CREDENTIAL_PATTERN =
  /(\b(?:token|password|secret)\b\s*[:=]\s*)(?:"(?:\\[^\r\n]|[^"\\\r\n])*"|'(?:\\[^\r\n]|[^'\\\r\n])*'|[^\s,;&|"'\\]+)/giu;

function explicitSecretVariants(secrets: readonly string[]): string[] {
  const variants = new Set<string>();
  for (const secret of secrets) {
    if (secret.length === 0) continue;
    variants.add(secret);
    const jsonEscaped = JSON.stringify(secret).slice(1, -1);
    if (jsonEscaped.length > 0) variants.add(jsonEscaped);
  }
  return [...variants].sort((left, right) => right.length - left.length);
}

export function redactEvidenceText(
  text: string,
  secrets: readonly string[] = [],
): string {
  let redacted = text;
  for (const secret of explicitSecretVariants(secrets)) {
    redacted = redacted.split(secret).join(REDACTION);
  }

  return redacted
    .replace(JSON_CREDENTIAL_PATTERN, (_match, prefix: string, value: string) => {
      const quote = value.slice(0, 1);
      return `${prefix}${quote}${REDACTION}${quote}`;
    })
    .replace(AUTHORIZATION_PATTERN, `$1${REDACTION}`)
    .replace(CREDENTIAL_PATTERN, `$1${REDACTION}`);
}
