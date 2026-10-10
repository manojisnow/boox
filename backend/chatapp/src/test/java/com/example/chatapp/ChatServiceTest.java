package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.controller.ResetContextRequest;
import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ChatContextService;
import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import com.example.chatapp.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ChatServiceTest {
  @Mock private ChatEngine engine;
  @Mock private com.example.chatapp.agent.AgentRunRepository agentRuns;
  @Mock private ChatContextService chatContextService;
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    chatService = new ChatService(Map.of("server1", engine), chatContextService, agentRuns);
  }

  @Test
  void getServers_returnsServerList() {
    assertEquals(List.of("server1"), chatService.getServers());
  }

  @Test
  void getModels_returnsModels_whenEngineExists() {
    List<ModelInfo> models = List.of(new ModelInfo("m", "d"));
    when(engine.getModels()).thenReturn(models);
    assertEquals(models, chatService.getModels("server1"));
  }

  @Test
  void getModels_returnsEmpty_whenEngineMissing() {
    assertTrue(chatService.getModels("unknown").isEmpty());
  }

  @Test
  void sendMessage_returnsResult_whenEngineExists() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("server1");
    req.setMessage("hi");
    req.setModel("m");
    req.setSessionId("sid");
    req.setStream(false);
    Map<String, String> result = Map.of("role", "assistant", "content", "reply");
    when(engine.sendMessage(any(), any(), any(), any(), anyBoolean())).thenReturn(result);
    assertEquals(result, chatService.sendMessage(req));
  }

  @Test
  void sendMessage_passesImagesThroughToEngine() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("server1");
    req.setMessage("what is this");
    req.setModel("m");
    req.setSessionId("sid");
    req.setStream(false);
    req.setImages(List.of("img1"));
    when(engine.sendMessage(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("role", "assistant", "content", "ok"));
    chatService.sendMessage(req);
    verify(engine)
        .sendMessage(eq("what is this"), eq(List.of("img1")), eq("m"), eq("sid"), eq(false));
  }

  @Test
  void sendMessage_nullImages_passesEmptyListToEngine() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("server1");
    req.setMessage("hi");
    req.setModel("m");
    req.setSessionId("sid");
    req.setStream(false);
    when(engine.sendMessage(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("role", "assistant", "content", "ok"));
    chatService.sendMessage(req);
    verify(engine).sendMessage(eq("hi"), eq(List.of()), eq("m"), eq("sid"), eq(false));
  }

  @Test
  void sendMessage_returnsUnknown_whenEngineMissing() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("unknown");
    Map<String, String> result = chatService.sendMessage(req);
    assertEquals("assistant", result.get("role"));
    assertEquals("(unknown server)", result.get("content"));
  }

  @Test
  void resetContext_callsEngine_whenExists() {
    ResetContextRequest req = new ResetContextRequest();
    req.setServer("server1");
    req.setSessionId("sid");
    doNothing().when(engine).resetContext("sid");
    chatService.resetContext(req);
    verify(engine).resetContext("sid");
  }

  @Test
  void resetContext_warns_whenEngineMissing() {
    ResetContextRequest req = new ResetContextRequest();
    req.setServer("unknown");
    chatService.resetContext(req); // Should not throw
  }

  @Test
  void sendMessage_setsSystemPrompt_whenProvided() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("server1");
    req.setMessage("hi");
    req.setModel("m");
    req.setSessionId("sid");
    req.setStream(false);
    req.setSystemPrompt("Be nice");
    when(engine.sendMessage(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Map.of("role", "assistant", "content", "ok"));
    chatService.sendMessage(req);
    verify(chatContextService).setSystemPrompt("sid", "Be nice");
  }

  @Test
  void streamMessage_delegatesToEngine() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("server1");
    req.setMessage("hi");
    req.setModel("m");
    req.setSessionId("sid");
    SseEmitter emitter = new SseEmitter();
    chatService.streamMessage(req, emitter);
    verify(engine).streamMessage(eq("hi"), eq(List.of()), eq("m"), eq("sid"), eq(emitter));
  }

  @Test
  void streamMessage_setsSystemPrompt_whenProvided() {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("server1");
    req.setMessage("hi");
    req.setModel("m");
    req.setSessionId("sid");
    req.setSystemPrompt("Be helpful");
    SseEmitter emitter = new SseEmitter();
    chatService.streamMessage(req, emitter);
    verify(chatContextService).setSystemPrompt("sid", "Be helpful");
  }

  @Test
  void streamMessage_handlesUnknownServer() throws Exception {
    SendMessageRequest req = new SendMessageRequest();
    req.setServer("unknown");
    req.setMessage("hi");
    req.setSessionId("sid");
    SseEmitter emitter = mock(SseEmitter.class);
    chatService.streamMessage(req, emitter);
    verify(emitter).complete();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void generationSettings_canBeSavedPreservedAndExplicitlyCleared(final boolean streaming)
      throws Exception {
    final SendMessageRequest request = new SendMessageRequest();
    request.setServer("server1");
    request.setMessage("hi");
    request.setModel("m");
    request.setSessionId("sid");
    request.setStream(streaming);
    request.setTemperature(0.4);
    request.setNumCtx(4096);
    request.setStopSequences(List.of("END"));
    sendRequest(request, streaming);
    verify(chatContextService)
        .setGenerationConfig(
            eq("sid"),
            argThat(
                config ->
                    Double.valueOf(0.4).equals(config.getTemperature())
                        && Integer.valueOf(4096).equals(config.getNumCtx())
                        && config.getStopSequences().equals(List.of("END"))));

    // A client that does not send settings must retain the previously saved overrides.
    clearInvocations(chatContextService);
    request.setTemperature(null);
    request.setNumCtx(null);
    request.setStopSequences(null);
    sendRequest(request, streaming);
    verify(chatContextService, never()).setGenerationConfig(any(), any());

    // Exercise JSON binding as well as both service paths for an explicit reset request.
    final SendMessageRequest reset =
        new ObjectMapper()
            .readValue(
                """
                {"server":"server1","model":"m","message":"hi","sessionId":"sid",
                 "stream":false,"replaceGenerationConfig":true}
                """,
                SendMessageRequest.class);
    sendRequest(reset, streaming);
    verify(chatContextService)
        .setGenerationConfig(
            eq("sid"),
            argThat(
                config ->
                    config.getTemperature() == null
                        && config.getNumCtx() == null
                        && config.getStopSequences().isEmpty()));
  }

  @Test
  void activeAgentTaskBlocksChatAndContextReset() {
    when(agentRuns.existsByConversationIdAndStatusIn(eq("sid"), any())).thenReturn(true);
    final SendMessageRequest request = new SendMessageRequest();
    request.setSessionId("sid");
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> chatService.sendMessage(request));
    final ResetContextRequest reset = new ResetContextRequest();
    reset.setSessionId("sid");
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> chatService.resetContext(reset));
    verifyNoInteractions(engine);
  }

  private void sendRequest(final SendMessageRequest request, final boolean streaming) {
    if (streaming) {
      chatService.streamMessage(request, new SseEmitter());
    } else {
      chatService.sendMessage(request);
    }
  }
}
