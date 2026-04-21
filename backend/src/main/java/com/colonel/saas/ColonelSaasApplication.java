package com.colonel.saas;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.colonel.saas.mapper")
@EnableScheduling
public class ColonelSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ColonelSaasApplication.class, args);
    }
}
