package com.example.chatapp.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class OllamaChatEngineTest {
  @Mock RestTemplate restTemplate;
  @Mock ChatContextService chatContextService;

  OllamaChatEngine engine;

  @BeforeEach
  void setUp() {
    engine = new OllamaChatEngine(restTemplate, chatContextService);
    // Set required @Value fields via reflection
    setField(engine, "ollamaApiUrl", "http://localhost:11434");
    setField(engine, "tagsPath", "/api/tags");
    setField(engine, "chatPath", "/api/chat");
    setField(engine, "temperature", 0.7d);
    setField(engine, "modelDescription", "desc");
  }

  static void setField(Object target, String field, Object value) {
    try {
      java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void getModels_returnsModels_whenOllamaRunning() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(
            new ResponseEntity<>(Map.of("models", List.of(Map.of("name", "m1"))), HttpStatus.OK));
    // Act
    List<ModelInfo> models = engine.getModels();
    // Assert
    assertEquals(1, models.size());
    assertEquals("m1", models.get(0).getName());
    assertEquals("desc", models.get(0).getDescription());
  }

  @Test
  void getModels_returnsError_whenOllamaNotRunning() {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenThrow(new RuntimeException("fail"));
    // Act
    List<ModelInfo> models = engine.getModels();
    // Assert
    assertEquals(1, models.size());
    assertTrue(models.get(0).getName().contains("Server not running"));
  }

  @Test
  void getModels_returnsErrorFetchingModels_onException() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.OK));
    // Act
    List<ModelInfo> models = engine.getModels();
    // Assert
    assertEquals(0, models.size()); // fetchModelsFromOllama returns empty list
  }

  @Test
  void sendMessage_returnsReply_nonStreaming() throws Exception {
    // Arrange
    String sessionId = "sid";
    String model = "m";
    String message = "hello";
    List<Map<String, String>> context = List.of(Map.of("role", "user", "content", message));
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(context);
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                new ObjectMapper()
                    .writeValueAsString(
                        Map.of("message", Map.of("role", "assistant", "content", "hi"))),
                HttpStatus.OK));
    // Act
    Map<String, String> reply = engine.sendMessage(message, model, sessionId, false);
    // Assert
    assertEquals("assistant", reply.get("role"));
    assertEquals("hi", reply.get("content"));
    verify(chatContextService, times(1)).addMessage(eq(sessionId), eq("user"), eq(message));
    verify(chatContextService, times(1)).addMessage(eq(sessionId), eq("assistant"), eq("hi"));
  }

  @Test
  void sendMessage_returnsReply_streaming() throws Exception {
    // Arrange
    String sessionId = "sid";
    String model = "m";
    String message = "hello";
    List<Map<String, String>> context = List.of(Map.of("role", "user", "content", message));
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(context);
    String streamed =
        "{" + "\"message\": {\"role\": \"assistant\", \"content\": \"hi\"}}\n{" + "\"done\": true}";
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(streamed, HttpStatus.OK));
    // Act
    Map<String, String> reply = engine.sendMessage(message, model, sessionId, true);
    // Assert
    assertEquals("assistant", reply.get("role"));
    assertEquals("hi", reply.get("content"));
    verify(chatContextService, times(1)).addMessage(eq(sessionId), eq("user"), eq(message));
    verify(chatContextService, times(1)).addMessage(eq(sessionId), eq("assistant"), eq("hi"));
  }

  @Test
  void sendMessage_returnsError_whenOllamaNotRunning() {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenThrow(new RuntimeException("fail"));
    // Act
    Map<String, String> reply = engine.sendMessage("msg", "m", sessionId, false);
    // Assert
    assertTrue(reply.get("content").contains("Server not running"));
  }

  @Test
  void sendMessage_returnsError_onException() throws Exception {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new RuntimeException("fail"));
    // Act
    Map<String, String> reply = engine.sendMessage("msg", "m", sessionId, false);
    // Assert
    assertTrue(reply.get("content").contains("Error communicating with Ollama"));
  }

  @Test
  void sendMessage_returnsNoResponse_whenBodyNull() throws Exception {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    // Act
    Map<String, String> reply = engine.sendMessage("msg", "m", sessionId, false);
    // Assert
    assertTrue(reply.get("content").contains("No response"));
  }

  @Test
  void resetContext_delegatesToService() {
    // Act
    engine.resetContext("sid");
    // Assert
    verify(chatContextService).resetContext("sid");
  }
}
