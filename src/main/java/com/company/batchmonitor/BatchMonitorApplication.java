package com.company.batchmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BatchMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchMonitorApplication.class, args);
    }
}
