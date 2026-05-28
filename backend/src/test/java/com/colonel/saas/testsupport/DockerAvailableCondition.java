package com.colonel.saas.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JUnit 5 ExecutionCondition：检测 Docker 是否可用。
 * <p>
 * Docker 可用的判断逻辑（优先级递减）：
 * <ol>
 *   <li>Windows: 检查 DOCKER_HOST 环境变量或 docker.exe 是否在 PATH 中，
 *      同时检查 Docker Desktop 是否在运行（通过进程或 docker info）</li>
 *   <li>Linux/macOS: 检查 /var/run/docker.sock 是否存在</li>
 * </ol>
 * <p>
 * 在 CI 环境中，有 Docker 时显式设置环境变量 DOCKER_AVAILABLE=true 可绕过自动检测。
 */
public class DockerAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Docker is available");

    private static final ConditionEvaluationResult DISABLED =
            ConditionEvaluationResult.disabled("Docker is not available");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (isDockerAvailable()) {
            return ENABLED;
        }
        return DISABLED;
    }

    private boolean isDockerAvailable() {
        // 显式环境变量（CI 可强制开启）
        String envFlag = System.getenv("DOCKER_AVAILABLE");
        if ("true".equalsIgnoreCase(envFlag)) {
            return true;
        }
        if ("false".equalsIgnoreCase(envFlag)) {
            return false;
        }

        // Linux/macOS: Docker socket
        Path socket = Paths.get("/var/run/docker.sock");
        if (Files.exists(socket)) {
            return true;
        }

        // Windows: docker info 能执行即认为 Docker Desktop 在运行
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
