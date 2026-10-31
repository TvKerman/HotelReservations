package com.booking.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

import com.booking.common.config.OpenApiConfig;

@SpringBootApplication
@EnableDiscoveryClient
@Import(OpenApiConfig.class)
public class HotelServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(HotelServiceApplication.class, args);
  }
}
