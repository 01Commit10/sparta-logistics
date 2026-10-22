package com.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(
        basePackages = "com.hub.inventory.infrastructure.client"
)
@SpringBootApplication
public class HubMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HubMsApplication.class, args);
    }

}
