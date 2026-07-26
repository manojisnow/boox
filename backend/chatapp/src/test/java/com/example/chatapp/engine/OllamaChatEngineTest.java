package com.example.chatapp.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class OllamaChatEngineTest {
  @Mock RestTemplate restTemplate;
  @Mock ChatContextService chatContextService;
  @Mock ToolRegistry toolRegistry;

  OllamaChatEngine engine;

  @BeforeEach
  void setUp() {
    engine = new OllamaChatEngine(restTemplate, chatContextService, toolRegistry);
    // Set required @Value fields via reflection
    setField(engine, "ollamaApiUrl", "http://localhost:11434");
    setField(engine, "tagsPath", "/api/tags");
    setField(engine, "chatPath", "/api/chat");
    setField(engine, "temperature", 0.7d);
    setField(engine, "modelDescription", "desc");
    // Large budget + summary off by default so existing tests send the full context.
    setField(engine, "maxContextTokens", 100000);
    setField(engine, "summaryEnabled", false);
    setField(engine, "numCtx", 0);
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
  void getModels_exposesCapabilities_whenPresent() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(
            new ResponseEntity<>(
                Map.of(
                    "models",
                    List.of(
                        Map.of(
                            "name",
                            "vision-model",
                            "capabilities",
                            List.of("completion", "vision")),
                        Map.of("name", "text-model", "capabilities", List.of("completion")))),
                HttpStatus.OK));
    // Act
    List<ModelInfo> models = engine.getModels();
    // Assert
    assertTrue(models.get(0).getCapabilities().contains("vision"));
    assertFalse(models.get(1).getCapabilities().contains("vision"));
  }

  @Test
  void getModels_missingCapabilities_defaultsToEmpty() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(
            new ResponseEntity<>(Map.of("models", List.of(Map.of("name", "m1"))), HttpStatus.OK));
    // Act
    List<ModelInfo> models = engine.getModels();
    // Assert
    assertTrue(models.get(0).getCapabilities().isEmpty());
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
    List<Map<String, Object>> context = List.of(Map.of("role", "user", "content", message));
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
    verify(chatContextService, times(1))
        .addMessage(eq(sessionId), eq("user"), eq(message), eq(List.of()));
    verify(chatContextService, times(1)).addMessage(eq(sessionId), eq("assistant"), eq("hi"));
  }

  @Test
  void sendMessage_handlesToolCalls() throws Exception {
    // Arrange
    String sessionId = "sid";
    String model = "m";
    String message = "search for cats";
    List<Map<String, Object>> context = List.of(Map.of("role", "user", "content", message));
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(context);

    // First call returns a tool call
    String toolCallResponse =
        new ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "message",
                    Map.of(
                        "role",
                        "assistant",
                        "content",
                        "",
                        "tool_calls",
                        List.of(
                            Map.of(
                                "function",
                                Map.of(
                                    "name",
                                    "web_search",
                                    "arguments",
                                    Map.of("query", "cats")))))));
    // Second call returns a normal response
    String finalResponse =
        new ObjectMapper()
            .writeValueAsString(
                Map.of("message", Map.of("role", "assistant", "content", "Here are cats")));
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(toolCallResponse, HttpStatus.OK))
        .thenReturn(new ResponseEntity<>(finalResponse, HttpStatus.OK));
    when(toolRegistry.hasTools()).thenReturn(true);
    when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
    when(toolRegistry.executeTool(eq("web_search"), any())).thenReturn("Cat search results");

    // Act
    Map<String, String> reply = engine.sendMessage(message, model, sessionId, false);

    // Assert
    assertEquals("assistant", reply.get("role"));
    assertEquals("Here are cats", reply.get("content"));
    verify(toolRegistry).executeTool(eq("web_search"), any());
    verify(chatContextService).addMessage(eq(sessionId), eq("tool"), eq("Cat search results"));
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
  void sendMessage_withSystemPrompt() throws Exception {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());
    when(chatContextService.getSystemPrompt(sessionId)).thenReturn("Be helpful");
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                new ObjectMapper()
                    .writeValueAsString(
                        Map.of("message", Map.of("role", "assistant", "content", "ok"))),
                HttpStatus.OK));
    // Act
    Map<String, String> reply = engine.sendMessage("hi", "m", sessionId, false);
    // Assert
    assertEquals("ok", reply.get("content"));
  }

  @Test
  void streamMessage_sendsChunksViaEmitter() throws Exception {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());
    // callOllamaWithTools uses exchange(); return a no-tool-calls response so code falls through
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}", HttpStatus.OK));

    String streamData =
        "{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}\n"
            + "{\"message\":{\"role\":\"assistant\",\"content\":\" world\"}}\n"
            + "{\"done\":true}\n";
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(streamData.getBytes(StandardCharsets.UTF_8));

    when(restTemplate.execute(anyString(), eq(HttpMethod.POST), any(), any()))
        .thenAnswer(
            inv -> {
              ResponseExtractor<?> extractor = inv.getArgument(3);
              ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
              when(mockResponse.getBody()).thenReturn(inputStream);
              return extractor.extractData(mockResponse);
            });

    SseEmitter emitter = mock(SseEmitter.class);

    // Act
    engine.streamMessage("hi", "m", sessionId, emitter);

    // Assert
    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
    verify(chatContextService).addMessage(eq(sessionId), eq("user"), eq("hi"), eq(List.of()));
    verify(chatContextService).addMessage(eq(sessionId), eq("assistant"), anyString());
  }

  @Test
  void streamMessage_handlesOllamaNotRunning() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenThrow(new RuntimeException("fail"));
    SseEmitter emitter = mock(SseEmitter.class);

    // Act
    engine.streamMessage("hi", "m", "sid", emitter);

    // Assert
    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
  }

  @Test
  void streamMessage_handlesException() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext("sid")).thenReturn(List.of());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}", HttpStatus.OK));
    when(restTemplate.execute(anyString(), eq(HttpMethod.POST), any(), any()))
        .thenThrow(new RuntimeException("connection error"));
    SseEmitter emitter = mock(SseEmitter.class);

    // Act
    engine.streamMessage("hi", "m", "sid", emitter);

    // Assert
    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
  }

  @Test
  void streamMessage_handlesEmitterError() throws Exception {
    // Arrange
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext("sid")).thenReturn(List.of());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}", HttpStatus.OK));
    when(restTemplate.execute(anyString(), eq(HttpMethod.POST), any(), any()))
        .thenThrow(new RuntimeException("fail"));
    SseEmitter emitter = mock(SseEmitter.class);
    doThrow(new IOException("emitter closed"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    // Act
    engine.streamMessage("hi", "m", "sid", emitter);

    // Assert
    verify(emitter).completeWithError(any());
  }

  @Test
  void streamMessage_persistsResponse_whenClientDisconnectsMidStream() throws Exception {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"}}", HttpStatus.OK));

    String streamData =
        "{\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}\n"
            + "{\"message\":{\"role\":\"assistant\",\"content\":\" world\"}}\n"
            + "{\"done\":true}\n";
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(streamData.getBytes(StandardCharsets.UTF_8));
    when(restTemplate.execute(anyString(), eq(HttpMethod.POST), any(), any()))
        .thenAnswer(
            inv -> {
              ResponseExtractor<?> extractor = inv.getArgument(3);
              ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
              when(mockResponse.getBody()).thenReturn(inputStream);
              return extractor.extractData(mockResponse);
            });

    SseEmitter emitter = mock(SseEmitter.class);
    // Simulate the client navigating away: every emit fails.
    doThrow(new IOException("broken pipe"))
        .when(emitter)
        .send(any(SseEmitter.SseEventBuilder.class));

    // Act
    engine.streamMessage("hi", "m", sessionId, emitter);

    // Assert: the full assistant reply is still persisted despite the disconnect,
    // and we do not try to complete a dead emitter.
    verify(chatContextService).addMessage(eq(sessionId), eq("assistant"), eq("Hello world"));
    verify(emitter, never()).complete();
  }

  @Test
  void sendMessage_summarizesDroppedMessages_whenBudgetExceeded() throws Exception {
    // Arrange
    setField(engine, "summaryEnabled", true);
    setField(engine, "maxContextTokens", 25);
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    List<Map<String, Object>> ctx =
        List.of(
            Map.of("role", "user", "content", "a".repeat(40)),
            Map.of("role", "assistant", "content", "b".repeat(40)),
            Map.of("role", "user", "content", "c".repeat(40)),
            Map.of("role", "assistant", "content", "d".repeat(40)));
    when(chatContextService.getContext(sessionId)).thenReturn(ctx);
    when(chatContextService.getSummarizedCount(sessionId)).thenReturn(0);
    when(chatContextService.getSummary(sessionId)).thenReturn(null);
    // First exchange = summarize call; second = tool-loop final response (no tool calls).
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"SUMMARY\"}}", HttpStatus.OK))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"final answer\"}}",
                HttpStatus.OK));

    // Act
    Map<String, String> reply = engine.sendMessage("hello", "m", sessionId, false);

    // Assert: the two oldest messages were folded into a stored summary.
    verify(chatContextService).setSummaryState(eq(sessionId), eq("SUMMARY"), eq(2));
    assertEquals("final answer", reply.get("content"));
  }

  @Test
  void sendMessage_sendsGenerationOptions() throws Exception {
    // Arrange
    setField(engine, "numCtx", 4096);
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext("sid")).thenReturn(List.of());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}", HttpStatus.OK));
    ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);

    // Act
    engine.sendMessage("hi", "m", "sid", false);

    // Assert: temperature and num_ctx are nested under options (Ollama ignores top-level).
    verify(restTemplate)
        .exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
    Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
    Map<String, Object> options = (Map<String, Object>) body.get("options");
    assertEquals(0.7d, options.get("temperature"));
    assertEquals(4096, options.get("num_ctx"));
    assertNull(body.get("temperature"));
  }

  @Test
  void sendMessage_withImages_persistsThemOnTheUserMessage() throws Exception {
    // Arrange
    String sessionId = "sid";
    List<String> images = List.of("base64imgA", "base64imgB");
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}", HttpStatus.OK));

    // Act
    engine.sendMessage("look at this", images, "m", sessionId, false);

    // Assert
    verify(chatContextService)
        .addMessage(eq(sessionId), eq("user"), eq("look at this"), eq(images));
  }

  @Test
  void sendMessage_imageBearingContextMessage_isForwardedToOllama() throws Exception {
    // Arrange: simulate a persisted context whose user turn carries images (as a real
    // ChatContextService implementation would return after addMessage(..., images)).
    String sessionId = "sid";
    Map<String, Object> imageMessage = new HashMap<>();
    imageMessage.put("role", "user");
    imageMessage.put("content", "what is this");
    imageMessage.put("images", List.of("base64imgA"));
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of(imageMessage));
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                "{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}", HttpStatus.OK));
    ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);

    // Act
    engine.sendMessage("what is this", List.of("base64imgA"), "m", sessionId, false);

    // Assert: the request sent to Ollama includes the images on that message.
    verify(restTemplate)
        .exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class));
    Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
    List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
    Map<String, Object> sentUserMessage =
        messages.stream().filter(m -> "user".equals(m.get("role"))).findFirst().orElseThrow();
    assertEquals(List.of("base64imgA"), sentUserMessage.get("images"));
  }

  @Test
  void streamMessage_handlesToolCalls() throws Exception {
    // Arrange
    String sessionId = "sid";
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext(sessionId)).thenReturn(List.of());

    // First call returns a tool call, second returns no tool calls
    String toolCallResponse =
        new ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "message",
                    Map.of(
                        "role",
                        "assistant",
                        "content",
                        "",
                        "tool_calls",
                        List.of(
                            Map.of(
                                "function",
                                Map.of(
                                    "name",
                                    "web_search",
                                    "arguments",
                                    Map.of("query", "cats")))))));
    String noToolResponse =
        new ObjectMapper()
            .writeValueAsString(Map.of("message", Map.of("role", "assistant", "content", "")));
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>(toolCallResponse, HttpStatus.OK))
        .thenReturn(new ResponseEntity<>(noToolResponse, HttpStatus.OK));
    when(toolRegistry.hasTools()).thenReturn(true);
    when(toolRegistry.getToolDefinitions()).thenReturn(List.of());
    when(toolRegistry.executeTool(eq("web_search"), any())).thenReturn("Cat results");

    String streamData =
        "{\"message\":{\"role\":\"assistant\",\"content\":\"Here\"}}\n{\"done\":true}\n";
    ByteArrayInputStream inputStream =
        new ByteArrayInputStream(streamData.getBytes(StandardCharsets.UTF_8));
    when(restTemplate.execute(anyString(), eq(HttpMethod.POST), any(), any()))
        .thenAnswer(
            inv -> {
              ResponseExtractor<?> extractor = inv.getArgument(3);
              ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
              when(mockResponse.getBody()).thenReturn(inputStream);
              return extractor.extractData(mockResponse);
            });

    SseEmitter emitter = mock(SseEmitter.class);

    // Act
    engine.streamMessage("hi", "m", sessionId, emitter);

    // Assert — tool_call and tool_result events were sent
    verify(toolRegistry).executeTool(eq("web_search"), any());
    verify(chatContextService).addMessage(eq(sessionId), eq("tool"), eq("Cat results"));
    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
  }

  @Test
  void streamMessage_handlesNullResponse() throws Exception {
    // Arrange - callOllamaWithTools returns null body
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK));
    when(chatContextService.getContext("sid")).thenReturn(List.of());
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    SseEmitter emitter = mock(SseEmitter.class);

    // Act
    engine.streamMessage("hi", "m", "sid", emitter);

    // Assert — NO_RESPONSE is sent
    verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
  }

  @Test
  void getModels_returnsError_whenFetchThrows() {
    // Arrange — isOllamaRunning succeeds, but fetchModelsFromOllama throws
    when(restTemplate.getForEntity(anyString(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(Map.of("models", List.of()), HttpStatus.OK))
        .thenThrow(new RuntimeException("parse error"));

    // Act
    List<ModelInfo> models = engine.getModels();

    // Assert — error fallback model is returned
    assertEquals(1, models.size());
    assertTrue(models.get(0).getName().contains("Error"));
  }

  @Test
  void resetContext_delegatesToService() {
    // Act
    engine.resetContext("sid");
    // Assert
    verify(chatContextService).resetContext("sid");
  }
}
