package com.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.order.infrastructure.client")
@SpringBootApplication
public class OrderMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderMsApplication.class, args);
    }

}
