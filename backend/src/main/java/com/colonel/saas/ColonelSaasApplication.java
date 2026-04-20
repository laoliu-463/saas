package com.colonel.saas;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.colonel.saas.mapper")
public class ColonelSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ColonelSaasApplication.class, args);
    }
}
