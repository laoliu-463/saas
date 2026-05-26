package com.colonel.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "logistics")
public class LogisticsProperties {

    /** mock | kuaidiniao | kuaidi100 */
    private String provider = "mock";

    private final Query query = new Query();
    private final Kdn kdn = new Kdn();
    private final Kd100 kd100 = new Kd100();
    private final Sync sync = new Sync();

    @Data
    public static class Query {
        private int timeoutSeconds = 10;
        private int retryCount = 1;
    }

    @Data
    public static class Kdn {
        private boolean enabled;
        private String ebusinessId;
        private String apiKey;
        private String endpoint = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";
        private boolean sandboxEnabled;
    }

    @Data
    public static class Kd100 {
        private boolean enabled;
        private boolean subscribeEnabled;
        private String customer;
        private String key;
        private String endpoint = "https://poll.kuaidi100.com/poll/query.do";
        private String subscribeEndpoint = "https://poll.kuaidi100.com/poll";
        private String callbackUrl;
        private String callbackSalt;
        private String resultV2 = "4";
        private boolean sandboxEnabled;
    }

    @Data
    public static class Sync {
        private boolean enabled;
        private String cron = "0 */30 * * * ?";
        private int batchSize = 100;
    }
}
