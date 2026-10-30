package com.booking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServiceApplication {

  public static void main(String[] args) {
    Hooks.enableAutomaticContextPropagation();
    SpringApplication.run(GatewayServiceApplication.class, args);
  }
}
