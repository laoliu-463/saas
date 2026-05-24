package com.colonel.saas.config;

import com.colonel.saas.service.talent.TalentCollectEnvironmentReporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TalentCollectStartupReporter implements ApplicationRunner {

    private final TalentCollectEnvironmentReporter environmentReporter;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Talent collect environment status={}", environmentReporter.resolveStatusLabel());
    }
}
