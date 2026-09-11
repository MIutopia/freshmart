package com.freshmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FreshMartApplication {
    public static void main(String[] args) {
        SpringApplication.run(FreshMartApplication.class, args);
    }
}
