import { redactEvidenceText } from "../core/redact.js";
import type { HealthAttemptEvidence, HealthLogInput } from "./health.js";
import { safeLogArtifactPath, writeSafeLog } from "./safe-log.js";

const HEALTH_LOG_IDS = new Set(["health-backend", "health-frontend"]);

export function healthLogArtifactPath(rawDir: string, probeId: string): string {
  return safeLogArtifactPath(rawDir, probeId, HEALTH_LOG_IDS);
}

function serializeAttempt(attempt: HealthAttemptEvidence): object {
  return {
    attempt: attempt.attempt,
    url: attempt.url,
    statusCode: attempt.statusCode,
    success: attempt.success,
    summary: attempt.summary,
  };
}

export function writeHealthCheckLog(input: HealthLogInput): Promise<string> {
  const content = redactEvidenceText(`${JSON.stringify({
    probeId: input.probeId,
    attempts: input.attempts.map(serializeAttempt),
  }, null, 2)}\n`);
  return writeSafeLog(
    {
      repoRoot: input.repoRoot,
      rawDir: input.rawDir,
      logId: input.probeId,
      content,
    },
    HEALTH_LOG_IDS,
  );
}
