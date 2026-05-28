package com.colonel.saas.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要 Docker 的测试。
 * <p>
 * 应用此注解后，若当前环境无 Docker（未启动 Docker Desktop，
 * 不存在 /var/run/docker.sock，docker info 失败），
 * 测试将被跳过（SKIPPED）而非失败。
 * <p>
 * 示例：
 * <pre>
 * &#64;ExtendWith(DockerAvailableCondition.class)
 * &#64;DockerAvailable
 * class MyIntegrationTest { ... }
 * </pre>
 *
 * @see DockerAvailableCondition
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface DockerAvailable {
}
