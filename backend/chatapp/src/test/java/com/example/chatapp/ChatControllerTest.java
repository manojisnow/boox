package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.controller.ChatController;
import com.example.chatapp.controller.ResetContextRequest;
import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ModelInfo;
import com.example.chatapp.service.ChatService;
import com.example.chatapp.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

class ChatControllerTest {
  @Mock private ChatService chatService;
  @Mock private ToolRegistry toolRegistry;

  @InjectMocks private ChatController chatController;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void getServers_returnsServers() {
    when(chatService.getServers()).thenReturn(List.of("server1", "server2"));
    List<String> servers = chatController.getServers();
    assertEquals(List.of("server1", "server2"), servers);
  }

  @Test
  void getModels_returnsModelInfos() {
    List<ModelInfo> models = List.of(new ModelInfo("m1", "desc"));
    when(chatService.getModels("server1")).thenReturn(models);
    ResponseEntity<List<ModelInfo>> response = chatController.getModels("server1");
    assertEquals(models, response.getBody());
  }

  @Test
  void sendMessage_returnsResponse() {
    SendMessageRequest req = new SendMessageRequest();
    req.setMessage("hi");
    req.setServer("s");
    req.setModel("m");
    req.setSessionId("sid");
    req.setStream(true);
    Map<String, String> result = Map.of("role", "assistant", "content", "reply");
    when(chatService.sendMessage(req)).thenReturn(result);
    ResponseEntity<?> response = chatController.sendMessage(req);
    assertEquals(result, response.getBody());
  }

  @Test
  void getTools_returnsToolList() {
    List<Map<String, String>> tools =
        List.of(Map.of("name", "web_search", "description", "Search the web"));
    when(toolRegistry.getAvailableTools()).thenReturn(tools);
    ResponseEntity<List<Map<String, String>>> response = chatController.getTools();
    assertEquals(tools, response.getBody());
  }

  @Test
  void resetContext_returnsOk() {
    ResetContextRequest req = new ResetContextRequest();
    req.setServer("s");
    req.setSessionId("sid");
    doNothing().when(chatService).resetContext(req);
    ResponseEntity<?> response = chatController.resetContext(req);
    assertEquals(200, response.getStatusCodeValue());
  }
}
