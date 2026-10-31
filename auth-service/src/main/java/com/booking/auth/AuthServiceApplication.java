package com.booking.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

import com.booking.common.config.OpenApiConfig;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableDiscoveryClient
@Import(OpenApiConfig.class)
public class AuthServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
