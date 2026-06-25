package com.colonel.saas.architecture;

import com.colonel.saas.controller.SampleController;
import com.colonel.saas.domain.sample.application.SampleApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DDD-SAMPLE-001：寄样 HTTP 入口统一依赖 domain/application 的 SampleApplicationService。
 */
class DddSample001ApplicationServiceTest {

    @Test
    @DisplayName("SampleController 只注入统一寄样应用服务")
    void sampleControllerShouldDependOnUnifiedSampleApplicationService() {
        Constructor<?>[] constructors = SampleController.class.getConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .extracting(Class::getName)
                .containsExactly(SampleApplicationService.class.getName());

        assertThat(Arrays.stream(SampleController.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .toList())
                .containsExactly(SampleApplicationService.class.getName());
    }

    @Test
    @DisplayName("SampleController 不直接依赖 command/query 应用服务")
    void sampleControllerSourceShouldNotImportSplitApplicationServices() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/colonel/saas/controller/SampleController.java"));

        assertThat(source)
                .doesNotContain("SampleCommandApplicationService")
                .doesNotContain("SampleQueryApplicationService");
    }

    @Test
    @DisplayName("SampleController 不直接导入持久化 Mapper")
    void sampleControllerSourceShouldNotImportPersistenceMappers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/colonel/saas/controller/SampleController.java"));

        assertThat(source).doesNotContain("com.colonel.saas.mapper.");
    }
}
