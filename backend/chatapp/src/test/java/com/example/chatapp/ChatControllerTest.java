package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

  // Exercises @RequestParam binding through the MVC layer. Under Spring 6 this
  // fails unless the code is compiled with -parameters, so it guards against the
  // compiler flag regressing.
  @Test
  void getModels_bindsQueryParamThroughMvc() throws Exception {
    when(chatService.getModels("ollama")).thenReturn(List.of(new ModelInfo("m1", "desc")));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
    mockMvc
        .perform(get("/api/chat/models").param("server", "ollama"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("m1"));
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
    assertEquals(200, response.getStatusCode().value());
  }
}
