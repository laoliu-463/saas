package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DddEventNoExternalMqContractTest {

    private static final List<String> FORBIDDEN_DEPENDENCY_FRAGMENTS = List.of(
            "<artifactId>spring-kafka</artifactId>",
            "<artifactId>kafka-clients</artifactId>",
            "<artifactId>spring-rabbit</artifactId>",
            "<artifactId>spring-boot-starter-amqp</artifactId>",
            "<artifactId>amqp-client</artifactId>",
            "<artifactId>rocketmq-spring-boot-starter</artifactId>",
            "<artifactId>spring-boot-starter-activemq</artifactId>",
            "<artifactId>spring-boot-starter-artemis</artifactId>");

    private static final Pattern FORBIDDEN_MQ_IMPORT = Pattern.compile(
            "^import\\s+(org\\.springframework\\.kafka|org\\.apache\\.kafka"
                    + "|org\\.springframework\\.amqp|com\\.rabbitmq"
                    + "|org\\.apache\\.rocketmq|jakarta\\.jms|javax\\.jms)\\.",
            Pattern.MULTILINE);

    private static final List<String> FORBIDDEN_CONFIG_KEYS = List.of(
            "spring.kafka",
            "spring.rabbitmq",
            "spring.activemq",
            "spring.artemis",
            "spring.jms",
            "rocketmq.");

    @Test
    void backendBuildShouldNotDeclareExternalMqDependencies() throws IOException {
        String pom = readProjectFile("pom.xml");

        assertThat(pom)
                .as("V1 event closure must not require Kafka/RabbitMQ/RocketMQ/JMS dependencies")
                .doesNotContain(FORBIDDEN_DEPENDENCY_FRAGMENTS.toArray(String[]::new));
    }

    @Test
    void backendSourceShouldNotImportExternalMqClientApis() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path mainJava = backendRoot().resolve("src/main/java");

        try (Stream<Path> paths = Files.walk(mainJava)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            Matcher matcher = FORBIDDEN_MQ_IMPORT.matcher(Files.readString(path));
                            if (matcher.find()) {
                                violations.add(relativeBackendPath(path) + "|" + matcher.group(1));
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }

        assertThat(violations)
                .as("DDD event producers/consumers must stay on Spring events and Outbox ports, not MQ clients")
                .isEmpty();
    }

    @Test
    void runtimeConfigShouldNotRequireExternalMqBrokers() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path resources = backendRoot().resolve("src/main/resources");

        try (Stream<Path> paths = Files.walk(resources)) {
            paths.filter(DddEventNoExternalMqContractTest::isConfigFile)
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path).toLowerCase();
                            for (String key : FORBIDDEN_CONFIG_KEYS) {
                                if (source.contains(key)) {
                                    violations.add(relativeBackendPath(path) + "|" + key);
                                }
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to scan " + path, e);
                        }
                    });
        }

        assertThat(violations)
                .as("real-pre/test config must not require Kafka/RabbitMQ/RocketMQ/JMS brokers")
                .isEmpty();
    }

    @Test
    void eventRuntimeShouldRemainSpringEventAndOutboxBacked() throws IOException {
        String orderPublisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/order/event/InProcessOrderDomainEventPublisher.java");
        String productPublisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java");
        String samplePublisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/sample/event/SampleDomainEventPublisher.java");
        String dispatcher = readProjectFile("src/main/java/com/colonel/saas/job/DomainEventDispatcherJob.java");
        String eventContract = readRepoFile("docs/04-事件契约总表.md");

        assertThat(orderPublisher)
                .contains(
                        "ApplicationEventPublisher",
                        "OutboxEventAppender",
                        "applicationEventPublisher.publishEvent",
                        "outboxEventAppender.appendIfAbsent(");
        assertThat(productPublisher)
                .contains(
                        "ApplicationEventPublisher",
                        "OutboxEventAppender",
                        "applicationEventPublisher.publishEvent",
                        "outboxEventAppender.appendIfAbsent(");
        assertThat(samplePublisher)
                .contains(
                        "ApplicationEventPublisher",
                        "OutboxEventAppender",
                        "applicationEventPublisher.publishEvent",
                        "outboxEventAppender.appendIfAbsent(");
        assertThat(dispatcher)
                .contains(
                        "@ConditionalOnProperty(name = \"app.domain-event.dispatch-enabled\"",
                        "domainEventOutboxService.lockPendingEvents",
                        "domainEventOutboxService.markPublished",
                        "domainEventOutboxService.markFailed");
        assertThat(eventContract)
                .contains("V1", "Spring", "Outbox", "MQ");
    }

    private static boolean isConfigFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(backendRoot().resolve(relativePath)).replace("\r\n", "\n");
    }

    private static String readRepoFile(String relativePath) throws IOException {
        return Files.readString(repoRoot().resolve(relativePath)).replace("\r\n", "\n");
    }

    private static String relativeBackendPath(Path path) {
        return backendRoot().relativize(path).toString().replace('\\', '/');
    }

    private static Path backendRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("src/main/java"))) {
            return cwd;
        }
        return cwd.resolve("backend");
    }

    private static Path repoRoot() {
        Path backend = backendRoot();
        if (backend.getFileName() != null && "backend".equals(backend.getFileName().toString())) {
            return backend.getParent();
        }
        return backend;
    }
}
