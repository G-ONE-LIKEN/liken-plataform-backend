package com.plataforma.oracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OracleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OracleServiceApplication.class, args);
    }
}