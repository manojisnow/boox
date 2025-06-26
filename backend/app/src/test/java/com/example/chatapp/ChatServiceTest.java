package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.controller.ResetContextRequest;
import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ChatEngine;
import com.example.chatapp.engine.ModelInfo;
import com.example.chatapp.service.ChatService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ChatServiceTest {
  @Mock private ChatEngine engine;
  private ChatService chatService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    chatService = new ChatService(Map.of("server1", engine));
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
    when(engine.sendMessage(any(), any(), any(), anyBoolean())).thenReturn(result);
    assertEquals(result, chatService.sendMessage(req));
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
  void searchWeb_returnsStub() {
    assertTrue(chatService.searchWeb("q").contains("Search results for query: q"));
  }
}
