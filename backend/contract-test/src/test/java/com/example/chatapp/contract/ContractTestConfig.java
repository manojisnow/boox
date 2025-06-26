package com.example.chatapp.contract;

import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ContractTestConfig {
  @Bean(name = "ollama")
  @Primary
  public ChatEngine mockOllamaChatEngine() {
    return new ChatEngine() {
      @Override
      public List<ModelInfo> getModels() {
        return List.of();
      }

      @Override
      public Map<String, String> sendMessage(
          String message, String model, String sessionId, boolean stream) {
        return Map.of("role", "assistant", "content", "Hi!");
      }

      @Override
      public void resetContext(String sessionId) {}
    };
  }
}
