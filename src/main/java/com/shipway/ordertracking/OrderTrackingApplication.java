package com.shipway.ordertracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OrderTrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderTrackingApplication.class, args);
    }
}
