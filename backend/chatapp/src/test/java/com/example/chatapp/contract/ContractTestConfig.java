package com.example.chatapp.contract;

import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ContractTestConfig {
  @Bean
  @Primary
  public ChatEngine mockChatEngine() {
    return new ChatEngine() {
      @Override
      public List<ModelInfo> getModels() {
        return Collections.singletonList(new ModelInfo("mock-model", "mock-model"));
      }

      @Override
      public Map<String, String> sendMessage(
          String message, String model, String sessionId, boolean stream) {
        return Map.of("role", "assistant", "content", "Greetings!");
      }

      @Override
      public void resetContext(String sessionId) {}
    };
  }
}
