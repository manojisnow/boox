package com.example.chatapp.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SuppressWarnings("PMD.AtLeastOneConstructor")
@Configuration
public class CorsConfig implements WebMvcConfigurer {
  @Value("${chat.cors.allowed-origins}")
  private String allowedOrigins;

  @Value("${chat.cors.allowed-methods}")
  private String allowedMethods;

  @Value("${chat.cors.allowed-headers}")
  private String allowedHeaders;

  @Value("${chat.cors.allow-credentials}")
  private boolean allowCredentials;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins(allowedOrigins.split(","))
            .allowedMethods(allowedMethods.split(","))
            .allowedHeaders(allowedHeaders.split(","))
            .allowCredentials(allowCredentials);
  }
}
