package com.aetherterra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AetherTerraApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherTerraApplication.class, args);
    }
}
