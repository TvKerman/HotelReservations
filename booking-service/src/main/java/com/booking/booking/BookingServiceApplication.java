package com.booking.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

import com.booking.common.config.OpenApiConfig;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(OpenApiConfig.class)
public class BookingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(BookingServiceApplication.class, args);
  }
}
