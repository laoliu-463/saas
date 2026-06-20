package com.colonel.saas.domain.user.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.port.UserChannelCodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserChannelCodePolicyTest {

    private Set<String> occupiedChannelCodes;

    private UserChannelCodePolicy policy;

    @BeforeEach
    void setUp() {
        occupiedChannelCodes = new HashSet<>();
        policy = new UserChannelCodePolicy(occupiedChannelCodes::contains);
    }

    @Test
    void generateUnique_shouldNormalizeAndTruncateUsername() {
        String channelCode = policy.generateUnique(" Channel.User-ABC_1234567890 ");

        assertThat(channelCode).isEqualTo("channeluserabc_1");
    }

    @Test
    void generateUnique_blankAfterNormalize_shouldUseUserFallback() {
        String channelCode = policy.generateUnique(" 中文!@# ");

        assertThat(channelCode).isEqualTo("user");
    }

    @Test
    void generateUnique_existingBase_shouldAppendFourCharacterSuffix() {
        occupiedChannelCodes.add("channel");

        String channelCode = policy.generateUnique("channel");

        assertThat(channelCode).startsWith("channel");
        assertThat(channelCode).hasSize("channel".length() + 4);
    }

    @Test
    void generateUnique_allCandidatesConflicting_shouldThrow() {
        UserChannelCodeRegistry allOccupied = channelCode -> true;
        policy = new UserChannelCodePolicy(allOccupied);

        assertThatThrownBy(() -> policy.generateUnique("channel"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("生成用户渠道编码失败，请重试");
    }

    @Test
    void policy_shouldDependOnUserDomainPortNotPersistenceMapper() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/policy/UserChannelCodePolicy.java"));

        assertThat(source).contains("UserChannelCodeRegistry");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.SysUserMapper");
    }
}
